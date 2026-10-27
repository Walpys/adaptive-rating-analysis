package com.adaptiveRating.anomaly;

import com.adaptiveRating.exception.anomaly.AnomalyCalculationException;

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

    public double calculateScore(
            Double personalAnomaly,
            Double clusterAnomaly,
            Double temporalAnomaly,
            Double objectConsensusAnomaly
    ) {
        double activeWeightSum = 0.0;
        double weightedSum = 0.0;

        if (personalAnomaly != null && wPersonal > 0) {
            weightedSum += wPersonal * personalAnomaly;
            activeWeightSum += wPersonal;
        }
        if (clusterAnomaly != null && wCluster > 0) {
            weightedSum += wCluster * clusterAnomaly;
            activeWeightSum += wCluster;
        }
        if (temporalAnomaly != null && wTime > 0) {
            weightedSum += wTime * temporalAnomaly;
            activeWeightSum += wTime;
        }
        if (objectConsensusAnomaly != null && wObject > 0) {
            weightedSum += wObject * objectConsensusAnomaly;
            activeWeightSum += wObject;
        }

        if (activeWeightSum == 0.0) {
            throw new AnomalyCalculationException("Cannot calculate anomaly score: active weight sum is zero or all anomaly inputs are null.");
        }

        double finalScore = weightedSum / activeWeightSum;
        return Math.max(0.0, Math.min(1.0, finalScore));
    }
}