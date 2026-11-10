package io.github.walpys.adaptiverating.config;

import io.github.walpys.adaptiverating.exception.InvalidConfigurationException;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.time.Duration;
import java.util.List;

@Getter
@Builder(toBuilder = true)
public final class AnalyzerConfig {
    private final int minRatings;
    private final int kMeansClusters;
    private final int maxKMeansIterations;
    private final long kMeansSeed;
    private final double weightPersonal;
    private final double weightCluster;
    private final double weightTime;
    private final double weightObject;
    private final double suspiciousThreshold;
    private final double anomalyThreshold;

    @Singular
    private final List<WindowRule> temporalRules;

    public AnalyzerConfig(
            int minRatings,
            int kMeansClusters,
            int maxKMeansIterations,
            long kMeansSeed,
            double weightPersonal,
            double weightCluster,
            double weightTime,
            double weightObject,
            double suspiciousThreshold,
            double anomalyThreshold,
            List<WindowRule> temporalRules
    ) {
        if (suspiciousThreshold <= 0.0 || suspiciousThreshold >= 1.0) {
            throw new InvalidConfigurationException("Suspicious threshold must be strictly between 0 and 1.");
        }

        if (anomalyThreshold <= 0.0 || anomalyThreshold >= 1.0) {
            throw new InvalidConfigurationException("Anomaly threshold must be strictly between 0 and 1.");
        }

        if (suspiciousThreshold >= anomalyThreshold) {
            throw new InvalidConfigurationException("Suspicious threshold must be strictly less than anomaly threshold.");
        }

        double weightSum = weightPersonal + weightCluster + weightTime + weightObject;
        if (Math.abs(weightSum - 1.0) > 1e-6) {
            throw new InvalidConfigurationException("Sum of all anomaly weights (personal, cluster, time, object) must equal 1.0.");
        }

        List<WindowRule> safeRules = temporalRules != null ? List.copyOf(temporalRules) : List.of();

        for (WindowRule rule : safeRules) {
            if (rule.saturationCount() <= rule.maxNormal()) {
                throw new InvalidConfigurationException("saturationCount must be greater than maxNormal for all temporal window rules.");
            }
        }

        this.minRatings = minRatings;
        this.kMeansClusters = kMeansClusters;
        this.maxKMeansIterations = maxKMeansIterations;
        this.kMeansSeed = kMeansSeed;
        this.weightPersonal = weightPersonal;
        this.weightCluster = weightCluster;
        this.weightTime = weightTime;
        this.weightObject = weightObject;
        this.suspiciousThreshold = suspiciousThreshold;
        this.anomalyThreshold = anomalyThreshold;
        this.temporalRules = safeRules;
    }

    public static AnalyzerConfig defaultConfig() {
        return new AnalyzerConfig(
                20,
                3,
                100,
                42L,
                0.4,
                0.3,
                0.2,
                0.1,
                0.40,
                0.65,
                List.of(
                        new WindowRule(Duration.ofMinutes(1), 3, 20),
                        new WindowRule(Duration.ofHours(1), 10, 50),
                        new WindowRule(Duration.ofHours(24), 30, 150)
                )
        );
    }
}