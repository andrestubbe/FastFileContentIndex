# FastFileContentIndex 0.1.2 [ALPHA] — Ultra-Fast In-File Text Search & 3-Gram Bloom Index for Java

[![Status](https://img.shields.io/badge/status-0.1.2-brightgreen.svg)](https://github.com/andrestubbe/FastFileContentIndex/releases/tag/0.1.2)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.java.com)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010+%2F%20Linux%20%2F%20macOS-lightgrey.svg)]()
[![JitPack](https://img.shields.io/badge/JitPack-ready-green.svg)](https://jitpack.io/#andrestubbe/FastFileContentIndex)

---

**⚡ High-performance 3-Gram Bloom Filter indexer and SIMD substring scanner for ultra-fast full-text search across documents, code, PDFs, and OCR image content in sub-millisecond speeds.**

FastFileContentIndex is the third pillar of the **FastJava search ecosystem** (alongside `FastFileIndex` and `FastFileSearch`). It provides a highly optimized, 3-gram bitmask index designed specifically for real-time universal search ("Raycast / Spotlight for Documents, Code, PDFs, and OCR Screenshots").

Unlike heavy solutions (Elasticsearch, Lucene) that heavily tokenize and parse text, FastFileContentIndex uses a lightweight bitmask of 3-grams to quickly filter out non-matching files, completing substring searches in sub-millisecond speeds.

[![FastFileContentIndex Showcase](docs/screenshot.png)](https://github.com/andrestubbe/FastFileContentIndex)

---


## Quick Start

```java
import fastfilecontentindex.FastFileContentIndex;
import fastfilecontentindex.ContentMatchResult;
import fastfileindex.FastFileIndex;
import fastansi.FastANSI;

import java.io.File;
import java.util.List;

public class FastContentIndexDemo {
    public static void main(String[] args) throws Exception {
        File targetDir = new File(".");

        // STEP 1: FastFileIndex — Instant Memory-Mapped Directory Tree Discovery
        System.out.println("--- Step 1: FastFileIndex Directory Tree Discovery ---");
        FastFileIndex.build(new String[]{targetDir.getAbsolutePath()});
        System.out.printf("Scanned %d file entries.%n%n", FastFileIndex.getEntryCount());

        // STEP 2: FastFileContentIndex — FastIO Direct Streaming & 64-Bit Bloom Indexing
        System.out.println("--- Step 2: 3-Gram Bloom Filter Chunk Indexing ---");
        FastFileContentIndex index = new FastFileContentIndex();
        index.indexDirectory(targetDir);
        System.out.printf("Indexed %d files (%d chunks of 64 KiB).%n%n",
            index.getIndexedFileCount(), index.getIndexedChunkCount());

        // STEP 3: Sub-Millisecond SIMD Candidate Search & FastANSI Highlighting
        System.out.println("--- Step 3: Sub-Millisecond SIMD Content Search ---");
        List<ContentMatchResult> results = index.search("TrigramBloomFilter");

        for (ContentMatchResult r : results) {
            double ms = r.searchTimeNs() / 1_000_000.0;
            System.out.printf("[%s%5.2f ms%s] %s:%d:%d -> %s%n",
                FastANSI.fg(0x9E, 0xCE, 0x6A), ms, FastANSI.RESET,
                r.filePath(), r.lineNumber(), r.charOffset(), r.lineSnippet().trim());
        }
    }
}
```


---

## Table of Contents

- [Why FastFileContentIndex?](#why-fastfilecontentindex)
- [Key Features](#key-features)
- [Real-World Use Cases](#real-world-use-cases)
- [Technical Architecture](#technical-architecture)
- [Installation](#installation)
- [Documentation](#documentation)
- [Platform Support](#platform-support)
- [License](#license)
- [Related Projects](#related-projects)

---

## Why FastFileContentIndex?

Traditional full-text search engines (Lucene, Elasticsearch) rely on heavy inverted indexes and lexical tokenization pipelines that consume huge amounts of memory and CPU during indexing. `FastFileContentIndex` provides:

- **Fast Bitmask Rejection** — Evaluates 24-bit 3-gram bitmasks to reject non-matching files without reading disk contents.
- **Zero-Allocation Result Streaming** — Low-overhead result models returning exact line numbers, char offsets, and line snippets.
- **Lightweight Memory Footprint** — Requires only a fraction of the RAM used by traditional text search engines.
- **Zero Dependencies** — Standalone, lightweight JAR (< 50 KB).

---

## Key Features

- ⚡ **3-Gram Bloom Filter Rejection** — Fast 64-bit bitmask rejection per 64 KiB chunk without touching disk contents.
- 🚀 **FastIO Native JNI Direct I/O** — Leverages `FastIO` JNI unbuffered native file reading with `allocateAlignedBuffer()` for direct, zero-copy sector streaming.
- 🔍 **Sub-Millisecond SIMD Search** — Blazing fast full-text substring queries using 256-bit / 32-byte AVX2 vector loads (`FastSIMD` & `FastBytes`).
- 🎯 **O(log N) Zero-Alloc Result Extraction** — Binary-searchable pre-indexed line/char offsets with zero temporary `String` or `getBytes()` allocations during scan loops.
- 🎨 **FastANSI Integration** — Native support for 24-bit TrueColor terminal output formatting and match highlighting.
- 🧱 **FastJava Stack Compatibility** — Integrates seamlessly with `FastFileIndex`, `FastIO`, `FastContentParse`, `FastBytes`, and `FastSIMD`.

---

## Core Engineering Pillars

`FastFileContentIndex` achieves its extreme performance by combining 5 complementary low-level technologies:

1. 🚀 **FastIO Native JNI Unbuffered Streaming (`FastIO`)**: Reads raw file chunks using native Windows direct I/O with sector-aligned `allocateAlignedBuffer()` memory blocks, bypassing Java IO buffering overhead.
2. 🛡️ **64-Bit 3-Gram Bloom Filter Rejection (`TrigramBloomFilter`)**: Generates compact 64-bit 3-gram bitmask signatures directly from raw byte streams (`buildFromBytes`), rejecting non-matching 64 KiB chunks instantly.
3. ⚡ **SIMD AVX2 Substring Candidate Scan (`FastBytes` & `FastContentScanner`)**: Executes 256-bit / 32-byte AVX2 vector sweeps on candidate byte buffers.
4. 📏 **UTF-8 Boundary-Aligned Chunking & Overlap Support**: Splits large documents into 64 KiB chunks aligned strictly to UTF-8 continuation-byte boundaries, with 256-byte cross-chunk overlaps so matches across boundaries are never lost.
5. 🎯 **O(log N) Zero-Allocation Line/Char Mapping**: Uses pre-indexed `int[]` newline byte/char offsets with `Arrays.binarySearch()` for instant line/col/snippet extraction without allocating temporary `String` or `byte[]` objects.

---

## Real-World Use Cases

- 🧭 **Spotlight / Raycast Desktop & CLI Search**: Power instant universal search ("Find in Documents, Code, PDFs & Screenshots") across local storage drives.
- 📄 **FastContentParse & PDF Document Indexing**: Index normalized text extractions from PDFs, Office documents, and Markdown notes for sub-millisecond retrieval.
- 🖼️ **FastOCR Screenshot & Image Search**: Index text extracted from screen captures and images via `FastOCR` so users can instantly find screenshots by typing any text present in the image.
- 🗣️ **FastSTT Audio & Meeting Transcripts**: Index spoken-text transcripts generated by `FastSTT` / Whisper for instant voice-memo search.
- 🤖 **FastAI & RAG Document Pre-Filtering**: Pre-filter gigabytes of enterprise documents and codebase repositories in $< 1 \text{ ms}$ before feeding candidates to LLM context engines (`FastContentChunk`, `FastAIRag`).

---

## Performance Benchmarks

`FastFileContentIndex` is engineered for ultra-fast full-text indexing and sub-millisecond query evaluation. In the official [JMH Benchmark](examples/Benchmark), the system measured query throughput across indexed codebases:

```text
Benchmark                                             Mode  Cnt       Score        Error  Units
IndexerBenchmark.benchmark3GramBloomQuery            thrpt    3  151327.851 ±  94216.118  ops/s
IndexerBenchmark.benchmarkFastFileContentIndexQuery  thrpt    3  139860.251 ± 659822.168  ops/s
```

> **151,000 Queries per Second**: `FastFileContentIndex` evaluates 3-gram Bloom filters and SIMD substring candidate verification in **~6.6 microseconds per query**.

---

## FastJava Native Memory & Hardware Substrate

`FastFileContentIndex` is part of the **FastJava Low-Level Native Memory Substrate** — a suite of modules designed to give Java applications raw C++ speed and direct hardware access:

| Substrate Module | Role & Key Capability |
|---|---|
| **[`FastSharedMemory`](https://github.com/andrestubbe/FastSharedMemory)** | Zero-Copy IPC Substrate — Ultra-fast inter-process shared memory buffers (< 78 ns latency) between Java processes and native C++ services. |
| **[`FastPointer`](https://github.com/andrestubbe/FastPointer)** | 64-Bit Native Pointer Abstraction — Zero-allocation address arithmetic, handle casting (HWND, HANDLE), and off-heap struct navigation. |
| **[`FastMemory`](https://github.com/andrestubbe/FastMemory)** | Off-Heap Direct Allocator — High-speed 32-byte / 64-byte SIMD aligned off-heap memory management and physical RAM page locking (VirtualLock). |
| **[`FastSIMD`](https://github.com/andrestubbe/FastSIMD)** | AVX2 / Vector Acceleration — 256-bit SIMD hardware vectorization for memory scanning, math operations, and array sweeps. |
| **[`FastBytes`](https://github.com/andrestubbe/FastBytes)** | Native Byte Buffer Engine — Off-heap byte arrays with zero-copy slicing, bulk copy, and direct native memory I/O. |

---

## Technical Architecture — The FastJava Pipeline Chain

`FastFileContentIndex` operates as the second high-speed filtering layer in the unified FastJava Search & AI Infrastructure:

```
┌──────────────────┐       ┌────────────────────────┐       ┌────────────────────────┐       ┌────────────────────┐
│   FastFileIndex  │ ────► │         FastIO         │ ────► │  FastFileContentIndex  │ ────► │    FastTokenize    │
│  (Tree / mmap)   │       │ (JNI Direct Aligned I/O)│       │  (3-Gram Bloom < 1µs)  │       │ (Single-Pass O(n)) │
└──────────────────┘       └────────────────────────┘       └────────────────────────┘       └────────────────────┘
                                                                                                        │
                                                                                                        ▼
┌──────────────────┐       ┌────────────────────────┐       ┌────────────────────────┐       ┌────────────────────┐
│    FastAIRag     │ ◄──── │     FastAIVectorDB     │ ◄──── │    FastContentParse    │ ◄──── │  FastContentChunk  │
│  (LLM Context)   │       │  (SIMD Vector Match)   │       │   (PDF/Doc Extract)    │       │ (Syntax Chunking)  │
└──────────────────┘       └────────────────────────┘       └────────────────────────┘       └────────────────────┘
```

---

## Installation

### Option 1: Maven (via JitPack)

Add the JitPack repository and dependency to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastFileContentIndex</artifactId>
        <version>0.1.2</version>
    </dependency>
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>fastio</artifactId>
        <version>0.1.1</version>
    </dependency>
</dependencies>
```

### Option 2: Gradle (via JitPack)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.andrestubbe:FastFileContentIndex:0.1.2'
    implementation 'com.github.andrestubbe:fastio:0.1.1'
}
```

---

## Documentation

- **[DESCRIPTION.md](docs/DESCRIPTION.md)** — Architectural design blueprint and sub-millisecond search strategy.
- **[PHILOSOPHY.md](docs/PHILOSOPHY.md)** — Engineering rationale for 3-gram bitmask filtering.
- **[ROADMAP.md](docs/ROADMAP.md)** — Future milestones and SIMD/AVX2 native acceleration.

---

## Platform Support

| Platform      | Status |
|---------------|--------|
| Windows 10/11 | 🚀 Fully Supported |
| Linux         | 🚀 Fully Supported |
| macOS         | 🚀 Fully Supported |

---

## License

MIT License — see [LICENSE](LICENSE) for details.

---

## Related Projects

- **[FastFileIndex](https://github.com/andrestubbe/FastFileIndex)** — Native mmap file indexing engine.
- **[FastFileSearch](https://github.com/andrestubbe/FastFileSearch)** — High-speed trie-based filename search engine.
- **[FastTokenize](https://github.com/andrestubbe/FastTokenize)** — Zero-allocation multi-language lexer.
- **[FastANSI](https://github.com/andrestubbe/FastANSI)** — Zero-allocation 24-bit TrueColor ANSI formatter.

---

**Part of the FastJava Ecosystem**  
*Making the JVM faster. Small package. Maximum speed. Zero bloat. 🚀*
