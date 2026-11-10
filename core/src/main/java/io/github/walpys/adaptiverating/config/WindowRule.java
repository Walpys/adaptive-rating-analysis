package io.github.walpys.adaptiverating.config;

import io.github.walpys.adaptiverating.exception.anomaly.AnomalyValidationException;

import java.time.Duration;

public record WindowRule(Duration window, int maxNormal, int saturationCount) {
    public WindowRule {
        if (saturationCount <= maxNormal) {
            throw new AnomalyValidationException("saturationCount must be > maxNormal");
        }
    }

    public double score(double count) {
        if (count <= maxNormal) return 0.0;
        double s = Math.log(count / maxNormal)
                / Math.log((double) saturationCount / maxNormal);
        return Math.min(1.0, s);
    }
}
