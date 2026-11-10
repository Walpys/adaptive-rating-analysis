# Adaptive Rating Analysis

Java 21 library for adaptive rating analysis and statistical anomaly detection. The project is organized as a Maven multi-module build:

- `core` contains the public API, rating/profile models, data-source SPI, clustering and anomaly-analysis implementation.
- `benchmarks` contains JMH benchmarks for detector operations, consensus-index construction and the combined profile-scoring pipeline.

## Overview & Purpose

`adaptive-rating-analysis` is designed to protect rating and review systems (e.g., e-commerce platforms, streaming services, content catalogs) against manipulation, review bombing, and bot activity. Any platform relying on user feedback faces the challenge of artificial ratings; this library filters out suspicious or fraudulent scores programmatically before they distort aggregate statistics.

### Key Capabilities

- **Single-Rating Analysis (`analyze`)**: Evaluates individual incoming ratings against user history and community consensus to detect localized anomalies[cite: 2].
- **Profile-Level Analysis (`analyzeProfile`)**: Scans complete user interaction history to flag systemic abuse (e.g., *review bombing*, *bot accounts*, or artificial fan/hater bursts)[cite: 2, 3].
- **Multi-Factor Anomaly Detection**: Combines 4 profile-level and 3 individual-rating detectors rather than relying solely on simple arithmetic averages[cite: 2, 3]:
    1. **Personal / Dispersion**: Evaluates personal deviations and score variance[cite: 2].
    2. **Temporal**: Detects rapid rating spikes and volume bursts over specific time windows[cite: 2].
    3. **Cluster**: Compares user behavior against behavioral cohorts identified via K-Means clustering[cite: 2, 3].
    4. **Object Consensus**: Measures alignment with trusted community consensus for a specific target object[cite: 2].

### Architecture Highlights

- **Modular Design**: Separated into core engine logic and dedicated JMH performance benchmarks[cite: 3, 4].
- **Extensible Storage SPI (`RatingDataSource`)**: Pluggable data layer allowing seamless integration with in-memory stores, relational databases, or custom backends[cite: 2, 3, 4].
- **Weighted Aggregation**: Normalizes multi-signal inputs into a consolidated score in `[0, 1]` mapped to `NORMAL`, `SUSPICIOUS`, or `ANOMALOUS` classifications[cite: 2].

## Documentation

| Document | Description |
|---|---|
| [Algorithm specification](docs/algorithm.md) | Detector formulas, score aggregation, default constants, constraints and tuning guidance. |
| [Evaluation methodology](docs/evaluation.md) | Ground-truth evaluation procedure, per-scenario precision/recall and interpretation of the current report. |
| [Performance](docs/performance.md) | Time and memory complexity, in-memory limitations, large-data operation and JMH benchmark instructions. |
| [RatingDataSource SPI](docs/datasource-spi.md) | Contract and implementation guidance for connecting a custom storage backend. |
## Requirements

- JDK 21
- Maven (or the included Maven Wrapper)

## Build and test

Linux/macOS:

```bash
./mvnw clean verify