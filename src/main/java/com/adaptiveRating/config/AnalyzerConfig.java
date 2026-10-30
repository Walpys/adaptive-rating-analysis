package com.adaptiveRating.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@Getter
@RequiredArgsConstructor
public class AnalyzerConfig {
    private final int minRatings;
    private final int kMeansClusters;
    private final int maxKMeansIterations;
    private final long kMeansSeed;
    private final double weightPersonal;
    private final double weightCluster;
    private final double weightTime;
    private final double anomalyThreshold;
    private final Duration temporalWindow;
    private final int maxTemporalCountInWindow;
    private final double weightObject;

    public static AnalyzerConfig defaultConfig() {
        return new AnalyzerConfig(
                20,
                3,
                100,
                42L,
                0.2,
                0.3,
                0.3,
                0.60,
                Duration.ofHours(1),
                10,
                0.2);
    }
}
