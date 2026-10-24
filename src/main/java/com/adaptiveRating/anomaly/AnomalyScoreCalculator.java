package com.adaptiveRating.anomaly;

import java.util.Optional;

public class AnomalyScoreCalculator {

    private final double wPersonal;
    private final double wCluster;
    private final double wTime;
    private final double wObject;

    public AnomalyScoreCalculator(double wPersonal, double wCluster, double wTime, double wObject) {
        this.wPersonal = wPersonal;
        this.wCluster = wCluster;
        this.wTime = wTime;
        this.wObject = wObject;
    }

    public Optional<Double> calculateScore(
            Optional<Double> personalAnomaly,
            Optional<Double> clusterAnomaly,
            Optional<Double> temporalAnomaly,
            Optional<Double> objectConsensusAnomaly
    ) {
        double activeWeightSum = 0.0;
        double weightedSum = 0.0;

        if (personalAnomaly.isPresent() && wPersonal > 0) {
            weightedSum += wPersonal * personalAnomaly.get();
            activeWeightSum += wPersonal;
        }
        if (clusterAnomaly.isPresent() && wCluster > 0) {
            weightedSum += wCluster * clusterAnomaly.get();
            activeWeightSum += wCluster;
        }
        if (temporalAnomaly.isPresent() && wTime > 0) {
            weightedSum += wTime * temporalAnomaly.get();
            activeWeightSum += wTime;
        }
        if (objectConsensusAnomaly.isPresent() && wObject > 0) {
            weightedSum += wObject * objectConsensusAnomaly.get();
            activeWeightSum += wObject;
        }

        if (activeWeightSum == 0.0) {
            return Optional.empty();
        }

        double finalScore = weightedSum / activeWeightSum;
        return Optional.of(Math.max(0.0, Math.min(1.0, finalScore)));
    }
}