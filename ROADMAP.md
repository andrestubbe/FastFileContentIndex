# FastFileContentIndex Roadmap 🗺️

**Vision:** To provide the fastest possible native primitives for file content indexing by aggressively bypassing bottlenecks in standard Java.

## 🟢 v0.1.0: Initial Release (Current)
- [x] **Core Native Engine**: Basic JNI implementation.
- [x] **Blueprint Standards**: README, Reference, and Philosophy integration.
- [ ] **N-Gram Index**: 3-gram index over file content for substring filtering
- [ ] **Basic Performance Suite**: Initial benchmarks vs standard Java

## 🟡 v0.2.0: Content Indexing
- [ ] **N-Gram Filter**: Query → 3-grams → lookup in RAM → candidate filtering
- [ ] **SIMD Substring Scan**: AVX2/AVX-512 vectorized substring search
- [ ] **File Type Support**: Index .java, .kt, .xml, .md and other text files
- [ ] **Zero-Copy Access**: Off-heap mmap index, no full-text database

## 🟠 v0.5.0: Advanced Search
- [ ] **Method Search**: Pattern-based method finding with regex matching
- [ ] **Code Visualization**: Mini-UI for method extraction and display
- [ ] **FastFileSearch Integration**: Seamless integration with file name search
- [ ] **Performance Optimization**: 2-10ms on 200GB drives, 1-5ms on SSD

## 🔴 v1.0.0: Production Hardening
- [ ] **Full Stability Audit**: Long-run stress testing
- [ ] **Enterprise Support**: NUMA-awareness and Large Pages support
- [ ] **CLI Integration**: FastSearch command for instant file/content lookup

---
**Focus:** Performance is our USP. We optimize where Java stops.