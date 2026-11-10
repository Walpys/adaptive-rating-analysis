package io.github.walpys.adaptiverating.anomaly;

import io.github.walpys.adaptiverating.config.WindowRule;
import io.github.walpys.adaptiverating.model.Rating;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public class TemporalAnomalyDetector implements AnomalyDetector {

    private final List<WindowRule> rules;

    public TemporalAnomalyDetector(List<WindowRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static TemporalAnomalyDetector withDefaults() {
        return new TemporalAnomalyDetector(List.of(
                new WindowRule(Duration.ofMinutes(1), 3, 20),
                new WindowRule(Duration.ofHours(1), 10, 50),
                new WindowRule(Duration.ofHours(24), 30, 150)
        ));
    }

    @Override
    public double calculateAnomaly(Rating current, List<Rating> history) {
        if (history == null || history.isEmpty()) return 0.0;
        Instant t = current.getEffectiveTime();
        double best = 0.0;
        for (WindowRule rule : rules) {
            Instant before = t.minus(rule.window());
            Instant after = t.plus(rule.window());
            double back = 0.0, fwd = 0.0;
            for (Rating r : history) {
                Instant x = r.getEffectiveTime();
                double weight = r.isInitialCreation() ? 1.0 : 0.5;

                if (!x.isBefore(before) && !x.isAfter(t)) back += weight;
                if (!x.isBefore(t) && !x.isAfter(after)) fwd += weight;
            }
            best = Math.max(best, rule.score(Math.max(back, fwd)));
        }
        return best;
    }

    public Double calculateProfileAnomaly(List<Rating> history) {
        if (history == null || history.isEmpty()) return 0.0;

        List<InstantWeight> weightedTimes = history.stream()
                .map(r -> new InstantWeight(r.getEffectiveTime(), r.isInitialCreation() ? 1.0 : 0.5))
                .sorted(Comparator.comparing(InstantWeight::time))
                .toList();

        double best = 0.0;
        for (WindowRule rule : rules) {
            best = Math.max(best, rule.score(maxWeightInWindow(weightedTimes, rule.window())));
        }
        return best;
    }

    private double maxWeightInWindow(List<InstantWeight> sorted, Duration window) {
        double maxWeightSum = 0.0;
        double currentWeightSum = 0.0;
        int left = 0;

        for (int right = 0; right < sorted.size(); right++) {
            InstantWeight rightItem = sorted.get(right);
            currentWeightSum += rightItem.weight();
            Instant start = rightItem.time().minus(window);

            while (sorted.get(left).time().isBefore(start)) {
                currentWeightSum -= sorted.get(left).weight();
                left++;
            }
            maxWeightSum = Math.max(maxWeightSum, currentWeightSum);
        }
        return maxWeightSum;
    }

    private record InstantWeight(Instant time, double weight) {
    }
}