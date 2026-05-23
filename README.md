# FastFileContentIndex — High-speed in-file text indexing [v0.1.0]

**N-Gram based string indexer for ultra-fast full-text code search across gigabytes of files.**

[![Status](https://img.shields.io/badge/status-v0.1.0-brightgreen.svg)](https://github.com/andrestubbe/FastFileContentIndex/releases/tag/v0.1.0)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.java.com)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010+-lightgrey.svg)]()
[![JitPack](https://img.shields.io/badge/JitPack-ready-green.svg)](https://jitpack.io/#andrestubbe)

---

## Overview

**FastFileContentIndex** is the third pillar of the FastJava search ecosystem (alongside FastFileIndex and FastFileSearch). It provides a highly optimized, N-Gram bloom-filter index designed specifically for real-time code search ("Raycast for code").

Unlike heavy solutions (Elasticsearch, Lucene) that tokenize and parse full text, FastFileContentIndex uses a lightweight bitmask of 3-grams to instantly filter out 99.9% of files, allowing the final SIMD substring scan to complete in single-digit milliseconds across massive codebases.

## Current Status

Currently in design and prototyping phase. See DESCRIPTION.md and PRODUCT.md for the architectural blueprints and Windows Store deployment strategies.
