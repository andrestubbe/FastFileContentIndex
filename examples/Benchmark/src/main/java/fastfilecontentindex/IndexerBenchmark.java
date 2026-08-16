package fastfilecontentindex;

import org.openjdk.jmh.annotations.*;
import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class IndexerBenchmark {

    private FastFileContentIndex index;

    @Setup
    public void setup() throws Exception {
        index = new FastFileContentIndex();
        File targetDir = new File("src");
        if (!targetDir.exists()) {
            targetDir = new File("..");
        }
        index.indexDirectory(targetDir);
    }

    @Benchmark
    public List<ContentMatchResult> benchmark3GramBloomQuery() {
        return index.search("TrigramBloomFilter");
    }

    @Benchmark
    public List<ContentMatchResult> benchmarkFastFileContentIndexQuery() {
        return index.search("FastFileContentIndex");
    }
}
