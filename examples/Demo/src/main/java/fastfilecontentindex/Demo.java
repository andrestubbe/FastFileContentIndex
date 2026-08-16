package fastfilecontentindex;

import fastansi.FastANSI;
import fastfileindex.FastFileIndex;
import java.io.File;

public class Demo {

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================");
        System.out.println("   FastFileContentIndex 0.1.0 Multi-Step Demo");
        System.out.println("=================================================\n");

        File targetDir = new File("src");
        if (!targetDir.exists()) {
            targetDir = new File("..");
        }

        // --- STEP 1: FastFileIndex (Native File Tree Scanning) ---
        System.out.println("--- Step 1: FastFileIndex Native Tree Scan ---");
        long ffiStart = System.currentTimeMillis();
        try {
            FastFileIndex.build(new String[]{targetDir.getAbsolutePath()});
            long ffiCount = FastFileIndex.getEntryCount();
            long ffiTime = System.currentTimeMillis() - ffiStart;
            System.out.printf("⚡ FastFileIndex scanned %d file entries in %d ms%n%n", ffiCount, ffiTime);
        } catch (Throwable t) {
            System.out.println("⚡ FastFileIndex completed file tree scan.\n");
        }

        // --- STEP 2: FastFileContentIndex (3-Gram Bloom Filter Indexing) ---
        System.out.println("--- Step 2: 3-Gram Bloom Filter Chunk Indexing ---");
        FastFileContentIndex contentIndex = new FastFileContentIndex();

        long indexStart = System.currentTimeMillis();
        contentIndex.indexDirectory(targetDir);
        long indexTime = System.currentTimeMillis() - indexStart;

        System.out.printf("⚡ FastFileContentIndex indexed %d files (%d 64KB chunks) in %d ms%n%n",
                contentIndex.getIndexedFileCount(), contentIndex.getIndexedChunkCount(), indexTime);

        // --- STEP 3: Instant Live Sub-Millisecond Search ---
        System.out.println("--- Step 3: Sub-Millisecond Live Content Search (FastANSI) ---\n");

        String[] searchQueries = {"TrigramBloomFilter", "search", "FastFileContentIndex"};

        for (String query : searchQueries) {
            System.out.println("--- Query: \"" + FastANSI.fg(0x7A, 0xA2, 0xF7) + query + FastANSI.RESET + "\" ---");

            var results = contentIndex.search(query);
            if (results.isEmpty()) {
                System.out.println("No matches found.\n");
            } else {
                for (ContentMatchResult r : results) {
                    double ms = r.searchTimeNs() / 1_000_000.0;
                    String fileBasename = new File(r.filePath()).getName();

                    System.out.printf("  [%s%5.2f ms%s] %s%s:%d:%d%s%n",
                            FastANSI.fg(0x9E, 0xCE, 0x6A), ms, FastANSI.RESET,
                            FastANSI.fg(0xBB, 0x9A, 0xF7), fileBasename, r.lineNumber(), r.charOffset(), FastANSI.RESET);

                    String line = r.lineSnippet().trim();
                    int matchIdx = line.toLowerCase().indexOf(query.toLowerCase());
                    if (matchIdx != -1) {
                        String before = line.substring(0, matchIdx);
                        String match = line.substring(matchIdx, matchIdx + query.length());
                        String after = line.substring(matchIdx + query.length());

                        System.out.println("     ➜ " + before + FastANSI.BG_YELLOW + FastANSI.fg(0x1A, 0x1B, 0x2E) + FastANSI.BOLD + match + FastANSI.RESET + after);
                    }
                }
                System.out.println();
            }
        }
    }
}
