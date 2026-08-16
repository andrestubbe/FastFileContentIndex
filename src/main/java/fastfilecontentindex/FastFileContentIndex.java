package fastfilecontentindex;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ultra-fast 3-Gram Bloom Filter indexer & SIMD candidate scanner.
 * Architecture:
 *   File -> 64KB Chunks -> 64-bit TrigramBloomFilter per Chunk.
 *   Query -> Bloom Filter Test -> SIMD Scan ONLY on matching 64KB Chunk.
 * Includes Incremental Indexing via lastModified tracking.
 */
public class FastFileContentIndex {

    public static final int CHUNK_SIZE = 65536; // 64 KB Chunks

    public static class FileChunkIndex {
        public final String filePath;
        public final int chunkIndex;
        public final int startCharOffset;
        public final int startLineNumber;
        public final String chunkContent;
        public final TrigramBloomFilter bloomFilter;

        public FileChunkIndex(String filePath, int chunkIndex, int startCharOffset, int startLineNumber, String chunkContent, TrigramBloomFilter bloomFilter) {
            this.filePath = filePath;
            this.chunkIndex = chunkIndex;
            this.startCharOffset = startCharOffset;
            this.startLineNumber = startLineNumber;
            this.chunkContent = chunkContent;
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

        // Incremental Indexing Check: Skip if file hasn't changed
        Long previousMod = lastModifiedMap.get(path);
        if (previousMod != null && previousMod == lastMod) {
            return; // Already up-to-date!
        }

        // Remove old chunks if re-indexing modified file
        chunkList.removeIf(c -> c.filePath.equals(path));

        String content;
        try {
            content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (Throwable fallback) {
            try {
                content = Files.readString(file.toPath(), StandardCharsets.ISO_8859_1);
            } catch (Throwable ignored) {
                return;
            }
        }

        lastModifiedMap.put(path, lastMod);

        if (content.length() <= CHUNK_SIZE) {
            TrigramBloomFilter filter = TrigramBloomFilter.buildFromText(content);
            chunkList.add(new FileChunkIndex(path, 0, 0, 1, content, filter));
        } else {
            // 64 KB Block Chunking for massive files
            int numChunks = (content.length() + CHUNK_SIZE - 1) / CHUNK_SIZE;
            int currentLineNumber = 1;

            for (int i = 0; i < numChunks; i++) {
                int start = i * CHUNK_SIZE;
                int end = Math.min(start + CHUNK_SIZE, content.length());
                String chunkText = content.substring(start, end);

                TrigramBloomFilter filter = TrigramBloomFilter.buildFromText(chunkText);
                chunkList.add(new FileChunkIndex(path, i, start, currentLineNumber, chunkText, filter));

                // Count line numbers inside this chunk for accurate line tracking across chunks
                for (int c = 0; c < chunkText.length(); c++) {
                    if (chunkText.charAt(c) == '\n') {
                        currentLineNumber++;
                    }
                }
            }
        }
    }

    public void indexDirectory(File dir) throws IOException {
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

        for (FileChunkIndex chunk : chunkList) {
            // 1. 64-bit 3-Gram Bloom Filter test per 64KB Chunk
            if (!chunk.bloomFilter.mightContainQuery(queryLower)) {
                continue; // Rejected instantly!
            }

            // 2. SIMD Candidate Scan ONLY on the matched 64KB Chunk!
            String chunkText = chunk.chunkContent;
            String[] lines = chunkText.split("\n", -1);
            int currentLine = chunk.startLineNumber;

            for (String line : lines) {
                int idx;
                try {
                    idx = FastContentScanner.findSubstringSIMD(line, query);
                } catch (Throwable fallback) {
                    idx = line.toLowerCase().indexOf(queryLower);
                }

                if (idx != -1) {
                    long elapsedNs = System.nanoTime() - startTime;
                    results.add(new ContentMatchResult(chunk.filePath, currentLine, idx, line, elapsedNs));
                }
                currentLine++;
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
