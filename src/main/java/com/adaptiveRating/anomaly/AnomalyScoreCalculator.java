package com.adaptiveRating.anomaly;

import com.adaptiveRating.exception.anomaly.AnomalyCalculationException;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
public class AnomalyScoreCalculator {

    private final double wPersonal;
    private final double wCluster;
    private final double wTime;
    private final double wObject;

    public double calculateScore(
            Double personalAnomaly,
            Double clusterAnomaly,
            Double temporalAnomaly,
            Double objectConsensusAnomaly
    ) {
        return calculateCombinedScore(personalAnomaly, clusterAnomaly, temporalAnomaly, objectConsensusAnomaly, wPersonal, wCluster, wTime, wObject);
    }

    /**
     * Окремий метод для розрахунку загального скору профілю,
     * де першим параметром замість персонального йде дисперсія профілю (dispersionAnomaly).
     */
    public double calculateProfileScore(
            Double dispersionAnomaly,
            Double clusterAnomaly,
            Double temporalAnomaly,
            Double objectConsensusAnomaly
    ) {
        // Використовуємо ті самі вагові коефіцієнти, але семантично підставляємо дисперсію замість особистої аномалії оцінки
        return calculateCombinedScore(dispersionAnomaly, clusterAnomaly, temporalAnomaly, objectConsensusAnomaly, wPersonal, wCluster, wTime, wObject);
    }

    private double calculateCombinedScore(
            Double primaryAnomaly,
            Double clusterAnomaly,
            Double temporalAnomaly,
            Double objectConsensusAnomaly,
            double p1, double p2, double p3, double p4
    ) {
        double activeWeightSum = 0.0;
        double weightedSum = 0.0;
        List<Double> signals = new ArrayList<>();

        if (primaryAnomaly != null && p1 > 0) {
            weightedSum += p1 * primaryAnomaly;
            activeWeightSum += p1;
            signals.add(primaryAnomaly);
        }
        if (clusterAnomaly != null && p2 > 0) {
            weightedSum += p2 * clusterAnomaly;
            activeWeightSum += p2;
            signals.add(clusterAnomaly);
        }
        if (temporalAnomaly != null && p3 > 0) {
            weightedSum += p3 * temporalAnomaly;
            activeWeightSum += p3;
            signals.add(temporalAnomaly);
        }
        if (objectConsensusAnomaly != null && p4 > 0) {
            weightedSum += p4 * objectConsensusAnomaly;
            activeWeightSum += p4;
            signals.add(objectConsensusAnomaly);
        }

        if (activeWeightSum == 0.0 || signals.isEmpty()) {
            throw new AnomalyCalculationException("Cannot calculate anomaly score: active weight sum is zero or all anomaly inputs are null.");
        }

        double weightedScore = weightedSum / activeWeightSum;
        double maxSignal = Collections.max(signals);

        double finalScore = 0.4 * weightedScore + 0.6 * maxSignal;

        return Math.max(0.0, Math.min(1.0, finalScore));
    }
}