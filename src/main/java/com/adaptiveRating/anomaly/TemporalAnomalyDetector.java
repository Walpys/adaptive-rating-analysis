package com.adaptiveRating.anomaly;

import com.adaptiveRating.model.Rating;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class TemporalAnomalyDetector {

    private final Duration timeWindow;
    private final int maxNormalCount;

    public TemporalAnomalyDetector(Duration timeWindow, int maxNormalCount) {
        this.timeWindow = timeWindow;
        this.maxNormalCount = maxNormalCount;
    }

    public double calculateAnomaly(Rating currentRating, List<Rating> history) {
        Instant currentTime = currentRating.timestamp();
        Instant windowStart = currentTime.minus(timeWindow);

        int countInWindow = 0;
        for (Rating r : history) {
            Instant rTime = r.timestamp();
            if (!rTime.isBefore(windowStart) && !rTime.isAfter(currentTime)) {
                countInWindow++;
            }
        }

        if (countInWindow <= maxNormalCount) {
            return 0.0;
        }

        double excess = countInWindow - maxNormalCount;
        double score = excess / (double) (maxNormalCount * 2);

        return Math.max(0.0, Math.min(1.0, score));
    }
}
