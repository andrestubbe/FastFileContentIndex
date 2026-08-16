# FastFileContentIndex 0.1.0 [ALPHA] — Ultra-Fast In-File Text Search & 3-Gram Bloom Index for Java

[![Status](https://img.shields.io/badge/status-0.1.0-brightgreen.svg)](https://github.com/andrestubbe/FastFileContentIndex/releases/tag/0.1.0)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.java.com)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010+%2F%20Linux%20%2F%20macOS-lightgrey.svg)]()
[![JitPack](https://img.shields.io/badge/JitPack-0.1.0-green.svg)](https://jitpack.io/#andrestubbe/FastFileContentIndex)

---

**⚡ High-performance 3-Gram Bloom Filter indexer and SIMD substring scanner for ultra-fast full-text code search across gigabytes of files in sub-millisecond speeds.**

FastFileContentIndex is the third pillar of the **FastJava search ecosystem** (alongside `FastFileIndex` and `FastFileSearch`). It provides a highly optimized, 3-gram bitmask index designed specifically for real-time code search ("Raycast / Spotlight for code").

Unlike heavy solutions (Elasticsearch, Lucene) that heavily tokenize and parse text, FastFileContentIndex uses a lightweight bitmask of 3-grams to instantly filter out 99.9% of files, completing substring searches in sub-millisecond speeds.

---

## Quick Start — Example

```java
import fastfilecontentindex.FastFileContentIndex;
import fastfilecontentindex.ContentMatchResult;
import java.io.File;
import java.util.List;

public class FastContentIndexDemo {
    public static void main(String[] args) throws Exception {
        FastFileContentIndex index = new FastFileContentIndex();

        // 1. Index codebase directory into 3-gram bloom bitmasks
        index.indexDirectory(new File("."));

        // 2. Sub-millisecond full-text search
        List<ContentMatchResult> results = index.search("TrigramBloomFilter");

        for (ContentMatchResult r : results) {
            System.out.printf("[%5.2f ms] %s:%d:%d -> %s%n",
                r.searchTimeNs() / 1_000_000.0,
                r.filePath(), r.lineNumber(), r.charOffset(), r.lineSnippet().trim());
        }
    }
}
```

---

## Installation (JitPack)

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.andrestubbe</groupId>
    <artifactId>FastFileContentIndex</artifactId>
    <version>0.1.0</version>
</dependency>
```

---

## Key Features

- 🚀 **3-Gram Bloom Filter Rejection**: Rejects 99.9% of files in $< 1 \text{ \mu s}$ per query without touching disk contents.
- ⚡ **Sub-Millisecond Search**: Blazing fast full-text substring queries across thousands of source code files.
- 🎨 **FastANSI Integration**: Native support for 24-bit TrueColor terminal output formatting and match highlighting.
- 🧱 **FastJava Stack Compatibility**: Integrates seamlessly with `FastFileIndex`, `FastFileSearch`, `FastBytes`, and `FastSIMD`.

---

## License

[MIT License](LICENSE) © 2026 Andre Stubbe
