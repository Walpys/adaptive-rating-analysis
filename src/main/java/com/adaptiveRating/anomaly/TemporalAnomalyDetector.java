package com.adaptiveRating.anomaly;

import com.adaptiveRating.model.Rating;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
public class TemporalAnomalyDetector {

    private final Duration timeWindow;
    private final int maxNormalCount;


    public double calculateAnomaly(Rating currentRating, List<Rating> history) {
        if (history == null || history.isEmpty()) {
            return 0.0;
        }

        Instant currentTime = currentRating.getEffectiveTime();
        Instant windowStart = currentTime.minus(timeWindow);

        int countInWindow = 0;
        for (Rating r : history) {
            Instant rTime = r.getEffectiveTime();
            // Рахуємо кількість оцінок у часовому інтервалі [windowStart, currentTime]
            if (!rTime.isBefore(windowStart) && !rTime.isAfter(currentTime)) {
                countInWindow++;
            }
        }

        return evaluateScore(countInWindow);
    }

    public Double calculateProfileAnomaly(List<Rating> history) {
        if (history == null || history.isEmpty()) {
            return 0.0;
        }

        // Сортуємо історію за часом для коректного пошуку ковзного вікна
        List<Rating> sortedHistory = history.stream()
                .sorted(Comparator.comparing(Rating::getEffectiveTime))
                .toList();

        int maxCountInAnyWindow = 0;

        // Проходимо по кожній оцінці як по «правій межі» ковзного вікна
        for (int i = 0; i < sortedHistory.size(); i++) {
            Instant windowEnd = sortedHistory.get(i).getEffectiveTime();
            Instant windowStart = windowEnd.minus(timeWindow);

            int currentWindowCount = 0;
            // Рахуємо скільки оцінок потрапило у вікно [windowStart, windowEnd]
            for (int j = 0; j <= i; j++) {
                Instant rTime = sortedHistory.get(j).getEffectiveTime();
                if (!rTime.isBefore(windowStart)) {
                    currentWindowCount++;
                }
            }

            if (currentWindowCount > maxCountInAnyWindow) {
                maxCountInAnyWindow = currentWindowCount;
            }
        }

        return evaluateScore(maxCountInAnyWindow);
    }

    private double evaluateScore(int countInWindow) {
        if (countInWindow <= maxNormalCount) {
            return 0.0;
        }

        double excess = countInWindow - maxNormalCount;

        // Використовуємо tanh для плавного насичення від 0 до 1.
        // Коефіцієнт 0.4 регулює наскільки швидко крива росте (можна крутити під свої реалії).
        return Math.tanh(0.1 * excess);
    }
}