# Algorithm specification

## 1. Purpose and pipeline

`adaptive-rating-analysis` is a Java 21 library for profile-level and individual-rating anomaly analysis. It combines four profile signals and three individual-rating signals. All detector outputs and the final score are intended to lie in `[0, 1]`, where a larger value means a stronger anomaly signal.

Profile analysis (`AdaptiveRatingAnalyzer.analyzeProfile`) follows this sequence:

1. Load the stored `UserProfile` and the user's rating history.
2. Calculate dispersion, cluster, temporal and object-consensus signals.
3. Combine available signals with `AnomalyScoreCalculator.calculateProfileScore`.
4. Map the score to `NORMAL`, `SUSPICIOUS` or `ANOMALOUS`.

Individual-rating analysis (`analyze`) calculates personal deviation, temporal burst and object-consensus signals. The cluster signal is passed as `null` in this path, so its weight is excluded and the remaining active weights are renormalized.

## 2. Input model and constraints

| Item | Implemented constraint |
|---|---|
| Rating value | Finite values from `1.0` through `10.0`; `NaN` is rejected. |
| User/object IDs | Non-null and non-blank. |
| Rating timestamp | Required. `updatedAt` is optional. |
| User vector | Exactly 10 components, each in `[0, 1]`; sum must be within `1e-4` of `1.0`. |
| Profile total ratings | Must not be negative. |
| Default minimum ratings for profile building | `20`. |
| Default cluster count | `3`. Actual `k` is capped at the number of vectors. |
| Default maximum K-Means iterations | `100`. |
| K-Means seed | `42`, giving repeatable initialization for identical ordered input. |
| Score thresholds | `0 < suspiciousThreshold < anomalyThreshold < 1`. |
| Weights | Sum of the four configured weights must be within `1e-6` of `1.0`. |

The `Rating` record does not explicitly reject positive infinity, and `AnalyzerConfig` does not independently validate that `minRatings`, `kMeansClusters`, `maxKMeansIterations` and each individual weight are positive/non-negative. Callers should validate these operational settings themselves. The vector constructor checks bounds and sum, but does not explicitly reject every non-finite component; `NaN` can evade ordinary comparison checks. These are implementation limitations, not extra guarantees.

## 3. Profile representation

`UserProfileBuilder` creates a ten-dimensional normalized histogram. For each rating value `v`, the bucket is:

$$b(v) = \operatorname{clamp}(\lfloor v - 0.5 \rfloor + 1, 1, 10)$$

Vector component $i$ is the fraction of the user's ratings in bucket $i+1$:

$$x_i = \frac{\operatorname{count}(i+1)}{N}, \quad i = 0, \ldots, 9$$

The profile also stores arithmetic mean, median, population standard deviation, minimum, maximum and total count. The standard deviation uses denominator $N$, not $N-1$.

## 4. Detector formulas

### 4.1 Personal anomaly (individual rating)

The implementation floors the user's standard deviation at `0.5`:

$$\sigma' = \max(\sigma, 0.5), \quad z = \frac{|r - \mu|}{\sigma'}$$

$$A_{personal} = \min(1, z / 3)$$

### 4.2 Dispersion anomaly (whole profile)

The detector returns `0` when the profile has fewer than five ratings. Otherwise, with profile standard deviation $\sigma$:

$$A_{dispersion} = \begin{cases}
(0.6 - \sigma) / 0.6 & \sigma < 0.6 \\
0 & 0.6 \le \sigma \le 3.0 \\
\min(1, (\sigma - 3.0) / (4.55 - 3.0)) & \sigma > 3.0
\end{cases}$$

### 4.3 Temporal anomaly

Each `WindowRule` has window duration $T$, `maxNormal` $= m$ and `saturationCount` $= s$, with $s > m$. Its score for weighted event count $c$ is:

$$A_T(c) = \begin{cases}
0 & c \le m \\
\min\left(1, \frac{\ln(c / m)}{\ln(s / m)}\right) & c > m
\end{cases}$$

Default rules:

| Window | `maxNormal` | `saturationCount` |
|---|---:|---:|
| 1 minute | 3 | 20 |
| 1 hour | 10 | 50 |
| 24 hours | 30 | 150 |

For an event, `isInitialCreation()` is true when `updatedAt` is absent or within five seconds of `timestamp`. Initial creations have weight `1.0`; updates have weight `0.5`. Individual-rating analysis checks weighted counts before and after the current event for each configured window and takes the maximum score. Profile analysis sorts the history and finds the maximum weighted sliding-window count for each rule, then takes the maximum rule score.

### 4.4 Cluster anomaly

K-Means uses Euclidean distance over the ten vector components. The cluster model stores the p95 distance from members to their assigned center as each cluster radius. The radius is floored at `0.1` by the trainer.

Cluster anomaly is disabled (`0`) when the total number of clustered users is below `50`. A cluster is considered small when its size is less than:

$$\max(3, \lceil 0.03 \cdot U \rceil)$$

For a small cluster with at least one alternative center, the distance used is the minimum distance to another center plus `0.2`. Otherwise, it is the distance to the assigned center. If the stored p95 radius is unavailable, the fallback is `0.8`; if the resulting radius is below `0.1`, the divisor is replaced with `0.5`.

$$q = d / r_{p95}, \quad A_{cluster} = \min(1, \max(0, (q - 0.5) / 1.5))$$

An invalid cluster index returns `0.5`. A missing cluster index throws when the detector is actually evaluated, but the small-global-sample guard runs first.

### 4.5 Object-consensus anomaly

The active analyzer uses `ConsensusIndex`, which builds per-object global and per-cluster sufficient statistics: `sum`, `sumSquares` and `count`. When the current user belongs to the trusted-user set used to build the index, their own contribution is subtracted before scoring.

At least five other ratings are required. Sample variance is calculated with denominator $n-1$; the standard deviation used in the z-score is floored at `1.0`:

$$\mu = \frac{\sum x}{n}, \quad s^2 = \max\left(0, \frac{\sum x^2 - (\sum x)^2 / n}{n-1}\right)$$

$$z = \frac{|r - \mu|}{\max(\sqrt{s^2}, 1.0)}, \quad A_{object} = \min(1, \max(0, (z - 1) / 2))$$

Cluster statistics are preferred. If they cannot produce a score, the global object statistics are used. If neither is available, `ConsensusIndex.score` returns `NaN`; the individual-rating analyzer converts that to `0`. Profile consensus is the fraction of evaluable ratings whose object score is at least `0.5`; it is `0` when no rating can be evaluated.

## 5. Score aggregation and classification

For all non-null signals with positive weights, the calculator renormalizes the active weights:

$$S_w = \frac{\sum_i w_i a_i}{\sum_i w_i}, \quad S_{max} = \max_i(a_i)$$

$$S = \operatorname{clamp}_{[0, 1]}(0.4 S_w + 0.6 S_{max})$$

The `0.6` maximum-signal term deliberately lets one strong detector influence the result even when the weighted average is lower. If no active signal remains, an `AnomalyCalculationException` is thrown.

Default weights are `personal/dispersion = 0.4`, `cluster = 0.3`, `time = 0.2`, `object = 0.1`. Default classification thresholds are:

| Score | Classification |
|---|---|
| `< 0.40` | `NORMAL` |
| `0.40` through `< 0.65` | `SUSPICIOUS` |
| `>= 0.65` | `ANOMALOUS` |

The individual-rating path passes no cluster score, so its active weights are `0.4`, `0.2` and `0.1`, renormalized to sum to one. The profile path can use all four signals.

## 6. Tuning guidance

- Change one family of parameters at a time. Otherwise the evaluation result becomes a small exercise in guessing.
- Lower `anomalyThreshold` to increase recall at the likely cost of more false positives. Raise it to be more conservative.
- Tune `suspiciousThreshold` independently, keeping it strictly below `anomalyThreshold`.
- Increase temporal `maxNormal` to tolerate ordinary bursts; increase `saturationCount` to make saturation slower. Keep `saturationCount > maxNormal`.
- Change `MIN_GLOBAL_USERS_FOR_CLUSTERING` only after evaluating cluster stability across multiple dataset sizes. It is currently a hard-coded `50`, not an `AnalyzerConfig` option.
- Treat the cluster small-size percentage (`3%`) and minimum size (`3`) as hard-coded implementation constants too.
- Tune the personal standard-deviation floor (`0.5`), dispersion boundaries (`0.6`, `3.0`, `4.55`) and object minimum sample size (`5`) using labeled data, not intuition.
- Re-run the same labeled dataset after every tuning change and report both precision and recall. Do not optimize one metric in isolation.

## 7. Implementation caveats

- `ObjectConsensusAnomalyDetector` is also present and is used by the JMH benchmark, but the main `AdaptiveRatingAnalyzer` pipeline uses `ConsensusIndex` for object consensus.
- The profile path and individual-rating path do not use identical signal sets: cluster is only included in profile scoring.
- `AnalysisStatus` defines `INSUFFICIENT_DATA` and `PARTIAL`, but the shown `determineResolution` helper currently always sets `COMPLETE`.
- `RatingDataSource` is part of the exported public API. The module descriptor exports `api`, `model`, `model.enums`, `config` and `datasource`; detector implementation packages are not exported as public module packages.