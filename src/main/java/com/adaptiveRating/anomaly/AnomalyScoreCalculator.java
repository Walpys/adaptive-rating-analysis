package com.adaptiveRating.anomaly;

import com.adaptiveRating.exception.anomaly.AnomalyCalculationException;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
        double activeWeightSum = 0.0;
        double weightedSum = 0.0;
        List<Double> signals = new ArrayList<>();

        if (personalAnomaly != null && wPersonal > 0) {
            weightedSum += wPersonal * personalAnomaly;
            activeWeightSum += wPersonal;
            signals.add(personalAnomaly);
        }
        if (clusterAnomaly != null && wCluster > 0) {
            weightedSum += wCluster * clusterAnomaly;
            activeWeightSum += wCluster;
            signals.add(clusterAnomaly);
        }
        if (temporalAnomaly != null && wTime > 0) {
            weightedSum += wTime * temporalAnomaly;
            activeWeightSum += wTime;
            signals.add(temporalAnomaly);
        }
        if (objectConsensusAnomaly != null && wObject > 0) {
            weightedSum += wObject * objectConsensusAnomaly;
            activeWeightSum += wObject;
            signals.add(objectConsensusAnomaly);
        }

        if (activeWeightSum == 0.0 || signals.isEmpty()) {
            throw new AnomalyCalculationException("Cannot calculate anomaly score: active weight sum is zero or all anomaly inputs are null.");
        }

        // Базовий середньозважений бал
        double weightedScore = weightedSum / activeWeightSum;

        // Знаходимо максимальний індивідуальний сигнал серед активних детекторах
        double maxSignal = Collections.max(signals);

        // Плавна комбінація (50% загального контексту + 50% найсильнішого спрацьовування),
        // яка не дає «потонути» унікальним потужним аномаліям.
        double finalScore = 0.5 * weightedScore + 0.5 * maxSignal;

        return Math.max(0.0, Math.min(1.0, finalScore));
    }
}