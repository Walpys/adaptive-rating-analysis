# Evaluation methodology and results

## 1. Evaluation procedure

The project contains `EvaluationTest`, which loads a labeled ground-truth dataset, builds a shared in-memory data source, creates profiles, trains K-Means, assigns cluster indexes, builds the final analyzer and evaluates each profile with `analyzeProfile`.

The evaluation fixture represented in the included generated report contains 500 user profiles and 26,489 ratings:

| Label in ground truth | Profiles |
|---|---:|
| `NORMAL` | 442 |
| `ANOMALOUS` | 47 |
| `BORDERLINE` | 11 |
| **Total** | **500** |

The test treats `NORMAL` as the negative class and every classification other than `NORMAL` (`SUSPICIOUS` or `ANOMALOUS`) as a positive detection. For `BORDERLINE` examples, a detection is counted as a false positive and a non-detection as a true negative. Profiles below `minRatings` are counted as true negatives without running the profile detector. This is the test's convention, not a universal definition of borderline data.

Metrics are calculated as:

\[
Precision=\frac{TP}{TP+FP},\qquad Recall=\frac{TP}{TP+FN}
\]

A metric is shown as `N/A` when its denominator is zero. Per-scenario values below are copied from the generated `core/target/evaluation-report.md` contained in the supplied project archive.

## 2. Per-scenario results

| Scenario | Precision | Recall | TP | FP | FN | TN |
|---|---:|---:|---:|---:|---:|---:|
| BOTS | 1.00 | 1.00 | 3 | 0 | 0 | 0 |
| HATERS | 1.00 | 1.00 | 4 | 0 | 0 | 0 |
| FANS | 1.00 | 0.75 | 3 | 0 | 1 | 0 |
| LEGIT_IMPORTERS | N/A | N/A | 0 | 0 | 0 | 4 |
| HOURLY_BURSTS | 1.00 | 0.75 | 9 | 0 | 3 | 9 |
| DAILY_BURSTS | 1.00 | 1.00 | 2 | 0 | 0 | 1 |
| OLD_BURSTS | 1.00 | 0.75 | 3 | 0 | 1 | 0 |
| CONSTANT | 1.00 | 1.00 | 4 | 0 | 0 | 0 |
| POLARIZED | 1.00 | 1.00 | 5 | 0 | 0 | 0 |
| RANDOM | N/A | 0.00 | 0 | 0 | 5 | 0 |
| LOW_SD_EDGE | 0.00 | N/A | 0 | 1 | 0 | 3 |
| CONTRARIANS | N/A | 0.00 | 0 | 0 | 4 | 0 |
| REVIEW_BOMBING | N/A | N/A | 0 | 0 | 0 | 14 |
| SPARSE_USERS | N/A | N/A | 0 | 0 | 0 | 8 |
| NORMAL | N/A | N/A | 0 | 0 | 0 | 413 |

## 3. Micro-averaged result

Summing the scenario confusion matrices gives:

| Metric | Value |
|---|---:|
| TP | 33 |
| FP | 1 |
| FN | 14 |
| TN | 452 |
| Precision | **97.06%** |
| Recall | **70.21%** |
| F1 | **81.48%** |

These are micro-averaged values over the report's 500 profiles. The high precision and lower recall mean the current configuration is conservative: it rarely flags a negative example, but it misses a meaningful share of labeled anomalous profiles. In particular, the report shows misses in `RANDOM` and `CONTRARIANS`, as well as some `FANS`, `HOURLY_BURSTS` and `OLD_BURSTS` profiles.

## 4. Interpretation and limitations

1. Perfect per-scenario precision does not mean the detector is generally perfect. Several scenarios have no predicted positives, so precision is `N/A`, while recall is zero for `RANDOM` and `CONTRARIANS`.
2. The overall `97.06%` precision is based on only one false positive. It should be interpreted together with recall and the dataset composition.
3. The `NORMAL` class contributes 413 true negatives, so class imbalance matters. Always retain the confusion matrix and per-scenario metrics.
4. The test labels borderline detections as false positives. A separate three-way evaluation could report borderline results independently instead of folding them into the negative class.
5. Users with fewer than the configured minimum number of ratings are automatically counted as TN by the test. This can inflate apparent specificity and should be reported explicitly.
6. The generated report is a snapshot from the supplied archive. The evaluation test expects `ground_truth.json` on the test classpath, but the archive does not include its source under `core/src/test/resources`; it is present under the build output `core/target/classes`. For a clean, reproducible checkout, keep the dataset in the source resources directory and version it with the project.
7. The supplied Surefire report records one evaluation test with zero failures and zero errors. This is evidence that the archived test run passed, not a guarantee that a fresh build from a clean checkout will pass while the source resource is missing.

## 5. Recommended next evaluation steps

- Restore/version `ground_truth.json` under `core/src/test/resources` so the evaluation can run from a clean checkout.
- Add a separate borderline column or a three-class confusion matrix.
- Report macro averages alongside micro averages, and include the number of examples behind every metric.
- Add threshold-sweep results (precision-recall curve) instead of selecting thresholds from one operating point.
- Add repeated runs with different seeds and shuffled input order. The configured seed makes a run repeatable for fixed input order, but does not establish robustness to dataset ordering.
- Preserve a held-out test set. Do not tune thresholds and report final performance on the same labeled examples.
