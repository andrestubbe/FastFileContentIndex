# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Initial project structure and documentation
- N-gram indexing architecture design
- File content search optimization specifications

### Planned
- N-Gram Index implementation (3-gram index over file content)
- N-Gram Filter implementation (query → 3-grams → lookup in RAM → candidate filtering)
- SIMD Substring Scan (AVX2/AVX-512 vectorized substring search)
- File Type Support (.java, .kt, .xml, .md and other text files)
- Zero-Copy Access (off-heap mmap index, no full-text database)
- Method Search (pattern-based method finding with regex matching)
- Code Visualization (mini-UI for method extraction and display)
- FastFileSearch Integration
- Performance Optimization (2-10ms on 200GB drives, 1-5ms on SSD)
- CLI Integration (FastSearch command for instant file/content lookup)

## [0.1.0] - 2026-05-23

### Added
- Project initialization
- Content indexing architecture design
- Standardized FastJava ecosystem module