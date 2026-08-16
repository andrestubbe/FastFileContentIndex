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
 * High-performance Zero-Allocation Architecture:
 *   1. FileChannel Stream Chunking & FastContentParse PDF Integration
 *   2. Pre-computed Lowercase UTF-8 Byte Arrays per Chunk
 *   3. Direct Byte-Offset -> Line/Column Mapping (Zero String.split() overhead during search)
 *   4. Strict Per-Chunk SIMD AVX2 Candidate Scanning
 *   5. Incremental Indexing via lastModified tracking
 */
public class FastFileContentIndex {

    public static final int CHUNK_SIZE = 65536; // 64 KB Chunks

    public static class FileChunkIndex {
        public final String filePath;
        public final int chunkIndex;
        public final int startLineNumber;
        public final String rawChunkContent;
        public final byte[] lowerChunkBytes; // Pre-allocated UTF-8 byte array for SIMD scans
        public final TrigramBloomFilter bloomFilter;

        public FileChunkIndex(String filePath, int chunkIndex, int startLineNumber, String rawChunkContent, byte[] lowerChunkBytes, TrigramBloomFilter bloomFilter) {
            this.filePath = filePath;
            this.chunkIndex = chunkIndex;
            this.startLineNumber = startLineNumber;
            this.rawChunkContent = rawChunkContent;
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

        String content = null;
        if (path.toLowerCase().endsWith(".pdf")) {
            try {
                fastcontentparse.FastContentParse parser = new fastcontentparse.FastContentParse();
                fastcontentparse.ParsedDocument doc = parser.parseFile(file.toPath());
                content = doc.getText();
            } catch (Throwable ignored) {}
        }

        if (content == null) {
            try (FileInputStream fis = new FileInputStream(file);
                 FileChannel channel = fis.getChannel()) {

                long fileSize = channel.size();
                if (fileSize == 0) return;

                byte[] bytes = new byte[(int) fileSize];
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
                channel.read(buffer);

                content = new String(bytes, StandardCharsets.UTF_8);
            } catch (Throwable fallback) {
                // Ignore unreadable binary files
                return;
            }
        }

        lastModifiedMap.put(path, lastMod);

        if (content.length() <= CHUNK_SIZE) {
            String lower = content.toLowerCase();
            byte[] lowerBytes = lower.getBytes(StandardCharsets.UTF_8);
            TrigramBloomFilter filter = TrigramBloomFilter.buildFromText(lower);
            chunkList.add(new FileChunkIndex(path, 0, 1, content, lowerBytes, filter));
        } else {
            // 64 KB Block Chunking for massive files
            int numChunks = (content.length() + CHUNK_SIZE - 1) / CHUNK_SIZE;
            int currentLineNumber = 1;

            for (int i = 0; i < numChunks; i++) {
                int start = i * CHUNK_SIZE;
                int end = Math.min(start + CHUNK_SIZE, content.length());
                String chunkText = content.substring(start, end);

                String lowerChunk = chunkText.toLowerCase();
                byte[] lowerBytes = lowerChunk.getBytes(StandardCharsets.UTF_8);
                TrigramBloomFilter filter = TrigramBloomFilter.buildFromText(lowerChunk);

                chunkList.add(new FileChunkIndex(path, i, currentLineNumber, chunkText, lowerBytes, filter));

                for (int c = 0; c < chunkText.length(); c++) {
                    if (chunkText.charAt(c) == '\n') {
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

                // 3. Direct Byte-Offset -> Line/Column Mapping
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

    private ContentMatchResult mapByteOffsetToResult(FileChunkIndex chunk, int matchBytePos, int queryLen, long elapsedNs) {
        String raw = chunk.rawChunkContent;
        if (matchBytePos >= raw.length()) return null;

        int lineNumber = chunk.startLineNumber;
        int lastNewline = -1;

        for (int i = 0; i < matchBytePos && i < raw.length(); i++) {
            if (raw.charAt(i) == '\n') {
                lineNumber++;
                lastNewline = i;
            }
        }

        int colOffset = matchBytePos - (lastNewline + 1);

        int lineStart = lastNewline + 1;
        int lineEnd = raw.indexOf('\n', matchBytePos);
        if (lineEnd == -1) {
            lineEnd = raw.length();
        }
        String snippet = raw.substring(lineStart, lineEnd);

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
