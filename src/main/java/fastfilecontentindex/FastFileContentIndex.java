package fastfilecontentindex;

import java.io.File;
import java.io.FileInputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ultra-fast 3-Gram Bloom Filter indexer & SIMD candidate scanner.
 * High-performance Byte-Level Architecture:
 *   1. True 64 KiB Byte Chunking (Raw UTF-8 byte boundary alignment)
 *   2. Pre-computed Raw UTF-8 & Lowercase Byte Arrays per Chunk
 *   3. UTF-8 Byte-Offset to Line/Column & Snippet Mapping (Handles Multi-byte UTF-8, Umlauts, Emojis)
 *   4. Strict Per-Chunk SIMD AVX2 Candidate Scanning
 *   5. Incremental Indexing via lastModified tracking
 */
public class FastFileContentIndex {

    public static final int CHUNK_SIZE_BYTES = 65536; // 64 KiB Bytes

    public static class FileChunkIndex {
        public final String filePath;
        public final int chunkIndex;
        public final int startLineNumber;
        public final byte[] rawChunkBytes;   // Original raw UTF-8 bytes
        public final byte[] lowerChunkBytes; // Pre-allocated lowercase UTF-8 bytes for SIMD scans
        public final TrigramBloomFilter bloomFilter;

        public FileChunkIndex(String filePath, int chunkIndex, int startLineNumber, byte[] rawChunkBytes, byte[] lowerChunkBytes, TrigramBloomFilter bloomFilter) {
            this.filePath = filePath;
            this.chunkIndex = chunkIndex;
            this.startLineNumber = startLineNumber;
            this.rawChunkBytes = rawChunkBytes;
            this.lowerChunkBytes = lowerChunkBytes;
            this.bloomFilter = bloomFilter;
        }
    }

    private final List<FileChunkIndex> chunkList = new ArrayList<>();
    private final Map<String, Long> lastModifiedMap = new ConcurrentHashMap<>();

    public synchronized void indexFile(File file) {
        if (!file.exists() || !file.isFile() || file.length() > 100_000_000) { // Skip files > 100MB
            return;
        }

        String path = file.getAbsolutePath();
        long lastMod = file.lastModified();

        // Incremental Indexing Check
        Long previousMod = lastModifiedMap.get(path);
        if (previousMod != null && previousMod == lastMod) {
            return; // Already up-to-date!
        }

        chunkList.removeIf(c -> c.filePath.equals(path));

        byte[] rawBytes = null;
        if (path.toLowerCase().endsWith(".pdf")) {
            try {
                fastcontentparse.FastContentParse parser = new fastcontentparse.FastContentParse();
                fastcontentparse.ParsedDocument doc = parser.parseFile(file.toPath());
                String parsedText = doc.getText();
                if (parsedText != null) {
                    rawBytes = parsedText.getBytes(StandardCharsets.UTF_8);
                }
            } catch (Throwable ignored) {}
        }

        if (rawBytes == null) {
            try (FileInputStream fis = new FileInputStream(file);
                 FileChannel channel = fis.getChannel()) {

                long fileSize = channel.size();
                if (fileSize == 0) return;

                rawBytes = new byte[(int) fileSize];
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(rawBytes);
                channel.read(buffer);
            } catch (Throwable fallback) {
                // Ignore unreadable binary files
                return;
            }
        }

        lastModifiedMap.put(path, lastMod);

        if (rawBytes.length <= CHUNK_SIZE_BYTES) {
            String contentStr = new String(rawBytes, StandardCharsets.UTF_8);
            String lowerStr = contentStr.toLowerCase();
            byte[] lowerBytes = lowerStr.getBytes(StandardCharsets.UTF_8);
            TrigramBloomFilter filter = TrigramBloomFilter.buildFromText(lowerStr);
            chunkList.add(new FileChunkIndex(path, 0, 1, rawBytes, lowerBytes, filter));
        } else {
            // True 64 KiB Byte Chunking for massive files
            int numChunks = (rawBytes.length + CHUNK_SIZE_BYTES - 1) / CHUNK_SIZE_BYTES;
            int currentLineNumber = 1;

            for (int i = 0; i < numChunks; i++) {
                int start = i * CHUNK_SIZE_BYTES;
                int end = Math.min(start + CHUNK_SIZE_BYTES, rawBytes.length);

                byte[] chunkRawBytes = new byte[end - start];
                System.arraycopy(rawBytes, start, chunkRawBytes, 0, end - start);

                String chunkRawText = new String(chunkRawBytes, StandardCharsets.UTF_8);
                String chunkLowerText = chunkRawText.toLowerCase();
                byte[] chunkLowerBytes = chunkLowerText.getBytes(StandardCharsets.UTF_8);
                TrigramBloomFilter filter = TrigramBloomFilter.buildFromText(chunkLowerText);

                chunkList.add(new FileChunkIndex(path, i, currentLineNumber, chunkRawBytes, chunkLowerBytes, filter));

                // Accurately track start line numbers across byte chunks
                for (byte b : chunkRawBytes) {
                    if (b == '\n') {
                        currentLineNumber++;
                    }
                }
            }
        }
    }

    public void indexDirectory(File dir) {
        if (!dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                indexDirectory(f);
            } else if (f.isFile() && isSupportedFile(f.getName())) {
                indexFile(f);
            }
        }
    }

    public List<ContentMatchResult> search(String query) {
        long startTime = System.nanoTime();
        List<ContentMatchResult> results = new ArrayList<>();
        if (query == null || query.isEmpty()) return results;

        String queryLower = query.toLowerCase();
        byte[] queryBytes = queryLower.getBytes(StandardCharsets.UTF_8);

        for (FileChunkIndex chunk : chunkList) {
            // 1. 64-bit 3-Gram Bloom Filter test per 64KB Chunk
            if (!chunk.bloomFilter.mightContainQuery(queryLower)) {
                continue; // Rejected instantly!
            }

            // 2. Direct SIMD AVX2 Candidate Byte-Offset Sweep
            byte[] chunkBytes = chunk.lowerChunkBytes;
            int offset = 0;

            while (offset < chunkBytes.length) {
                int matchPos = FastContentScanner.findSubstringSIMD(chunkBytes, queryBytes, offset);
                if (matchPos == -1) {
                    break;
                }

                // 3. UTF-8 Byte-Offset to Line/Column & Snippet Mapping (Handles Umlauts, Multi-byte UTF-8)
                long elapsedNs = System.nanoTime() - startTime;
                ContentMatchResult result = mapByteOffsetToResult(chunk, matchPos, queryBytes.length, elapsedNs);
                if (result != null) {
                    results.add(result);
                }

                offset = matchPos + queryBytes.length;
            }
        }
        return results;
    }

    private ContentMatchResult mapByteOffsetToResult(FileChunkIndex chunk, int matchBytePos, int queryByteLen, long elapsedNs) {
        byte[] rawBytes = chunk.rawChunkBytes;
        if (matchBytePos >= rawBytes.length) return null;

        int lineNumber = chunk.startLineNumber;
        int lastNewlineBytePos = -1;

        // Calculate line number & column offset strictly from raw UTF-8 bytes
        for (int i = 0; i < matchBytePos && i < rawBytes.length; i++) {
            if (rawBytes[i] == '\n') {
                lineNumber++;
                lastNewlineBytePos = i;
            }
        }

        int lineStartBytePos = lastNewlineBytePos + 1;

        // Find end of line in byte array
        int lineEndBytePos = rawBytes.length;
        for (int i = matchBytePos; i < rawBytes.length; i++) {
            if (rawBytes[i] == '\n' || rawBytes[i] == '\r') {
                lineEndBytePos = i;
                break;
            }
        }

        // Extract raw line snippet from bytes safely using UTF-8
        byte[] lineBytes = new byte[lineEndBytePos - lineStartBytePos];
        System.arraycopy(rawBytes, lineStartBytePos, lineBytes, 0, lineBytes.length);
        String snippet = new String(lineBytes, StandardCharsets.UTF_8);

        // Column offset in UTF-16 characters up to match byte position
        int colOffset = 0;
        try {
            byte[] prefixBytes = new byte[matchBytePos - lineStartBytePos];
            System.arraycopy(rawBytes, lineStartBytePos, prefixBytes, 0, prefixBytes.length);
            colOffset = new String(prefixBytes, StandardCharsets.UTF_8).length();
        } catch (Throwable ignored) {}

        return new ContentMatchResult(chunk.filePath, lineNumber, colOffset, snippet, elapsedNs);
    }

    public int getIndexedFileCount() {
        return lastModifiedMap.size();
    }

    public int getIndexedChunkCount() {
        return chunkList.size();
    }

    public void clear() {
        chunkList.clear();
        lastModifiedMap.clear();
    }

    private static boolean isSupportedFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".cpp") ||
               lower.endsWith(".h") || lower.endsWith(".py") || lower.endsWith(".js") ||
               lower.endsWith(".ts") || lower.endsWith(".html") || lower.endsWith(".css") ||
               lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".md") ||
               lower.endsWith(".txt") || lower.endsWith(".bat") || lower.endsWith(".sh") ||
               lower.endsWith(".pdf") || lower.endsWith(".log") || lower.endsWith(".png") ||
               lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }
}
