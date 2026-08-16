package fastfilecontentindex;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FastFileContentIndex {

    private final Map<String, TrigramBloomFilter> fileIndex = new ConcurrentHashMap<>();
    private final Map<String, String> contentCache = new ConcurrentHashMap<>();

    public void indexFile(File file) throws IOException {
        if (!file.exists() || !file.isFile() || file.length() > 50_000_000) { // Skip files > 50MB
            return;
        }
        String content = Files.readString(file.toPath());
        TrigramBloomFilter filter = TrigramBloomFilter.buildFromText(content);

        String path = file.getAbsolutePath();
        fileIndex.put(path, filter);
        contentCache.put(path, content);
    }

    public void indexDirectory(File dir) throws IOException {
        if (!dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                indexDirectory(f);
            } else if (f.isFile() && isTextFile(f.getName())) {
                indexFile(f);
            }
        }
    }

    public List<ContentMatchResult> search(String query) {
        long startTime = System.nanoTime();
        List<ContentMatchResult> results = new ArrayList<>();
        if (query == null || query.isEmpty()) return results;

        String queryLower = query.toLowerCase();

        for (Map.Entry<String, TrigramBloomFilter> entry : fileIndex.entrySet()) {
            String path = entry.getKey();
            TrigramBloomFilter filter = entry.getValue();

            // 1. Ultra-fast 3-Gram Bloom Filter test (< 1 microsecond rejection)
            if (!filter.mightContainQuery(queryLower)) {
                continue; // Rejected instantly!
            }

            // 2. Candidate verification
            String content = contentCache.get(path);
            if (content != null) {
                String[] lines = content.split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    int idx = line.toLowerCase().indexOf(queryLower);
                    if (idx != -1) {
                        long elapsedNs = System.nanoTime() - startTime;
                        results.add(new ContentMatchResult(path, i + 1, idx, line, elapsedNs));
                    }
                }
            }
        }
        return results;
    }

    public int getIndexedFileCount() {
        return fileIndex.size();
    }

    public void clear() {
        fileIndex.clear();
        contentCache.clear();
    }

    private static boolean isTextFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".cpp") ||
               lower.endsWith(".h") || lower.endsWith(".py") || lower.endsWith(".js") ||
               lower.endsWith(".ts") || lower.endsWith(".html") || lower.endsWith(".css") ||
               lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".md") ||
               lower.endsWith(".txt") || lower.endsWith(".bat") || lower.endsWith(".sh");
    }
}
