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
        Instant currentTime = currentRating.getEffectiveTime();
        Instant windowStart = currentTime.minus(timeWindow);

        int countInWindow = 0;
        for (Rating r : history) {
            Instant rTime = r.getEffectiveTime(); // Враховуємо оновлення або створення
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

    public Double calculateProfileAnomaly(List<Rating> history) {
        if (history == null || history.isEmpty()) {
            return 0.0;
        }

        Rating latestRating = history.stream()
                .max((r1, r2) -> r1.getEffectiveTime().compareTo(r2.getEffectiveTime()))
                .orElse(history.get(history.size() - 1));

        return calculateAnomaly(latestRating, history);
    }
}