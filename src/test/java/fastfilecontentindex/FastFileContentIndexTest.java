package fastfilecontentindex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FastFileContentIndexTest {

    private FastFileContentIndex index;

    @BeforeEach
    void setUp() {
        index = new FastFileContentIndex();
    }

    @Test
    void testTrigramBloomFilterRejection() {
        TrigramBloomFilter filter = TrigramBloomFilter.buildFromText("public static void main");
        assertTrue(filter.mightContainQuery("main"));
        assertTrue(filter.mightContainQuery("static"));
        assertFalse(filter.mightContainQuery("nonexistentxyz"));
    }

    @Test
    void testIndexAndSearch(@TempDir Path tempDir) throws IOException {
        Path file1 = tempDir.resolve("TestClass.java");
        Files.writeString(file1, "public class TestClass {\n    public static void main(String[] args) {}\n}");

        Path file2 = tempDir.resolve("README.md");
        Files.writeString(file2, "# Sample Project\nThis is a test readme.");

        index.indexDirectory(tempDir.toFile());

        assertEquals(2, index.getIndexedFileCount());

        List<ContentMatchResult> results = index.search("TestClass");
        assertFalse(results.isEmpty());
        assertEquals("TestClass.java", new File(results.get(0).filePath()).getName());

        List<ContentMatchResult> noResults = index.search("UnknownString123");
        assertTrue(noResults.isEmpty());
    }

    @Test
    void test64KbChunking(@TempDir Path tempDir) throws IOException {
        Path largeFile = tempDir.resolve("large_log.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            sb.append("Log line ").append(i).append(": Everything operating normally.\n");
        }
        sb.append("Log line 3001: CRITICAL_ERROR_CODE_99\n");
        Files.writeString(largeFile, sb.toString());

        index.indexFile(largeFile.toFile());

        assertTrue(index.getIndexedChunkCount() > 1); // Verified 64KB chunking

        List<ContentMatchResult> errorResults = index.search("CRITICAL_ERROR_CODE_99");
        assertFalse(errorResults.isEmpty());
        assertTrue(errorResults.get(0).lineSnippet().contains("CRITICAL_ERROR_CODE_99"));
    }

    @Test
    void testIncrementalIndexing(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Dynamic.java");
        Files.writeString(file, "class Dynamic { void first() {} }");

        index.indexFile(file.toFile());
        assertEquals(1, index.getIndexedFileCount());
        assertFalse(index.search("first").isEmpty());
        assertTrue(index.search("second").isEmpty());

        // Modify file content and ensure re-indexing updates chunks
        file.toFile().setLastModified(System.currentTimeMillis() + 2000);
        Files.writeString(file, "class Dynamic { void second() {} }");

        index.indexFile(file.toFile());
        assertEquals(1, index.getIndexedFileCount());
        assertTrue(index.search("first").isEmpty());
        assertFalse(index.search("second").isEmpty());
    }
}
