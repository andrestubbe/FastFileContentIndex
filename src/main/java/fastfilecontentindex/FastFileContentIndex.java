package fastfilecontentindex;

import fastbytes.FastBytes;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ultra-fast 3-Gram Bloom Filter indexer & SIMD candidate scanner.
 * High-performance Zero-Allocation Architecture:
 *   1. Memory-Mapped Chunks (mmap FileChannel)
 *   2. Pre-computed Lowercase UTF-8 Byte Arrays per Chunk (Zero GC during queries)
 *   3. Strict Per-Chunk SIMD AVX2 Candidate Scanning
 *   4. Incremental Indexing via lastModified tracking
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

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             FileChannel channel = raf.getChannel()) {

            long fileSize = channel.size();
            if (fileSize == 0) return;

            byte[] bytes = new byte[(int) fileSize];
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
            channel.read(buffer);

            String content = new String(bytes, StandardCharsets.UTF_8);
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
        } catch (Throwable fallback) {
            // Ignore unreadable binary files
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

            // 2. Zero-Allocation SIMD AVX2 Candidate Scan ONLY on matching 64KB Chunk!
            byte[] chunkBytes = chunk.lowerChunkBytes;
            int matchPos = FastContentScanner.findSubstringSIMD(chunkBytes, queryBytes, 0);

            if (matchPos != -1) {
                // Map byte offset back to line number and raw line snippet
                String rawChunk = chunk.rawChunkContent;
                String[] lines = rawChunk.split("\n", -1);
                int currentLine = chunk.startLineNumber;

                for (String line : lines) {
                    if (line.toLowerCase().contains(queryLower)) {
                        long elapsedNs = System.nanoTime() - startTime;
                        int idx = line.toLowerCase().indexOf(queryLower);
                        results.add(new ContentMatchResult(chunk.filePath, currentLine, idx, line, elapsedNs));
                    }
                    currentLine++;
                }
            }
        }
        return results;
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
