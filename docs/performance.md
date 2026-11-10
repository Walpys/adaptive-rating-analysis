# Performance, complexity and large-data operation

## 1. Complexity summary

Notation:

- `R`: total ratings.
- `U`: distinct users.
- `O`: distinct rated objects.
- `k`: number of clusters.
- `d`: vector dimensions, fixed at `10` in this implementation.
- `I`: K-Means iterations, bounded by `maxKMeansIterations`.
- `r`: ratings in one user's history.
- `W`: number of temporal window rules, three by default.

| Operation | Time complexity | Additional memory |
|---|---:|---:|
| Build one profile | `O(r log r)` | `O(r)` |
| Assign one vector to nearest cluster | `O(kd)` | `O(1)` |
| K-Means training | `O(Ru * (I k d + k² d) + Ru log Ru)` where `Ru` is clustered users | `O(Ru + k d)` |
| Temporal profile anomaly | `O(r log r + rW)` | `O(r)` |
| Temporal single-rating anomaly | `O(rW)` | `O(1)` |
| Consensus index build | `O(R + U)` expected, excluding storage access costs | `O(O k + U)` |
| Profile consensus lookup | `O(r)` | `O(1)` |
| `getUserRatingsBatch` in memory | `O(number of requested users + returned ratings)` | proportional to returned data |
| In-memory full-rating snapshot | `O(R)` to rebuild | `O(R)` references in the snapshot |

`Ru` is used in the K-Means row to avoid confusing the number of ratings with the number of user vectors. K-Means++ initialization computes distances against the centers selected so far, giving an `O(Ru * k² * d)` upper-bound term. Each Lloyd iteration assigns each vector to each center and recomputes means. The final pass recomputes assignments and p95 radii; sorting the distances for p95 adds `O(Ru log Ru)` in total.

The fixed dimension `d=10` makes vector operations effectively constant-time for this version. If the vector dimension becomes configurable, retain `d` in capacity estimates.

## 2. Memory characteristics

### In-memory source

`InMemoryRatingDataSource` maintains:

- a user-to-ratings index;
- an object-to-ratings index;
- profile objects;
- an immutable full-rating snapshot, rebuilt after each `addRating`.

The indexes and snapshot hold references to the rating objects, but they still add substantial reference-array and collection overhead. The same logical rating is reachable through multiple indexes. `addRating` rebuilds the full snapshot, so repeated single-record writes can approach quadratic total copying over a long ingestion sequence. It is suitable for tests, small/medium datasets and read-heavy snapshots, not as an unbounded ingestion store.

### Consensus index

`ConsensusIndex` stores three doubles per object per slot, where slots represent the global population and up to `k` user clusters. The raw numeric payload is approximately:

\[
O\cdot 3(k+1)\cdot 8\text{ bytes}
\]

This excludes Java map entries, array headers, object keys and the temporary profile cache. The build cache can hold up to `U` profile references during construction. The index itself retains the per-object arrays, not the full rating list.

### Profiles and clustering

Each profile contains a ten-component vector and a ten-bucket count map, plus scalar statistics. K-Means keeps input vectors, center vectors, assignments and per-cluster member lists during training. Avoid building duplicate copies of all rating histories in application code when the data source can provide batches or streams.

## 3. Large-data operating mode

For large datasets, implement `RatingDataSource` against a database or another indexed store instead of loading all ratings into `InMemoryRatingDataSource`.

Recommended behavior:

1. Index ratings by `(user_id, timestamp)` for user history and temporal analysis.
2. Index by `(object_id, user_id)` for object-level lookups and self-exclusion.
3. Persist profiles and cluster assignments separately, with a version or training timestamp for the cluster model.
4. Implement `streamAllRatings()` with a database cursor or fetch-size-based streaming. Do not call `getAllRatings()` for the full dataset.
5. Implement `getRatingsBatch(offset, limit)` in the storage layer. For very large or frequently changing tables, prefer keyset pagination internally; the SPI currently exposes offset pagination, so stable ordering is required for consistent pages.
6. Implement `getUserRatingsBatch()` as one batched query, not one query per user. The analyzer processes trusted-user IDs in batches of 500.
7. Make returned collections immutable snapshots or otherwise document their consistency behavior. Close database resources when the returned stream is closed.
8. Build or refresh the consensus index as a separate controlled job. It scans all ratings and can be expensive; avoid rebuilding it for every request.
9. Use a stable dataset snapshot during cluster training and consensus-index construction. Mixing profile assignments from different model versions produces inconsistent cluster statistics.
10. Consider incremental aggregates for object statistics if ingestion volume makes full index rebuilds too expensive. Keep the self-exclusion semantics correct.

## 4. Existing JMH benchmark

The `benchmarks` Maven module contains `AnomalyDetectionBenchmark`. It parameterizes a synthetic workload at `1,000`, `10,000` and `50,000` ratings and measures:

- score calculation;
- personal anomaly;
- cluster anomaly;
- temporal profile anomaly;
- object-consensus profile anomaly;
- consensus-index construction;
- the combined profile scoring pipeline.

The benchmark configuration uses three warmup iterations, five measurement iterations, one fork, throughput and average-time modes, and microseconds as the output unit. `AnomalyBenchmarkRunner` adds `GCProfiler` and writes JSON to `benchmarks/target/jmh-reports/jmh-results.json`.

The archive contains benchmark source and generated build artifacts, but no JMH result JSON was supplied. Therefore this document intentionally does not invent latency, throughput or allocation numbers. Run the benchmark on the target hardware and record JVM version, CPU, memory, OS, heap settings and the resulting JSON before publishing numerical performance claims.

## 5. Commands

From the repository root, build the modules:

```bash
./mvnw clean verify
```

Build the benchmark artifact:

```bash
./mvnw -pl benchmarks -am package -Prun-benchmarks -DskipTests
```

Run the benchmark runner using the shaded artifact produced by the benchmark module (the exact artifact name is configured as `benchmarks.jar` in the `run-benchmarks` profile). The runner stores the JSON report under `benchmarks/target/jmh-reports/`.

For Windows, use `mvnw.cmd` instead of `./mvnw`.

## 6. Operational recommendations

- Keep `k` small and validate it against the number of eligible user profiles.
- Measure index-build time separately from per-profile scoring time.
- Track heap allocation and GC, not only elapsed time. The benchmark already configures `GCProfiler`.
- Benchmark cold and warm database/cache behavior separately.
- Use a fixed dataset snapshot and repeat measurements; one timing run is not a performance study.
- Do not use the in-memory source's thread-safety note as a promise that writes are safe. Its `addRating` and `saveProfile` methods are explicitly not thread-safe.
