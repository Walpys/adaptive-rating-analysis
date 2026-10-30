package com.adaptiveRating.anomaly;

import com.adaptiveRating.model.Rating;

import java.util.List;

public class ObjectConsensusAnomalyDetector {

    public double calculateAnomaly(Rating currentRating, List<Rating> objectRatingsInCluster) {
        if (objectRatingsInCluster == null || objectRatingsInCluster.isEmpty()) {
            return 0.0;
        }

        // 1. Збираємо суму та рахуємо кількість (виключаючи поточну оцінку)
        double sum = 0.0;
        int count = 0;

        for (Rating r : objectRatingsInCluster) {
            if (r.equals(currentRating)) {
                continue;
            }
            sum += r.rating();
            count++;
        }

        // Якщо в кластері немає інших оцінок для порівняння — аномалію виявити неможливо
        if (count == 0) {
            return 0.0;
        }

        // 2. Рахуємо середнє значення об'єкта (leave-one-out mean)
        double clusterObjectMean = sum / count;

        // 3. Рахуємо дисперсію та стандартне відхилення об'єкта у кластері (також leave-one-out)
        double sumSquaredDiff = 0.0;
        for (Rating r : objectRatingsInCluster) {
            if (r.equals(currentRating)) {
                continue;
            }
            double diffFromMean = r.rating() - clusterObjectMean;
            sumSquaredDiff += diffFromMean * diffFromMean;
        }

        double stdDev = Math.sqrt(sumSquaredDiff / count);

        // Якщо дисперсія 0 (усі інші користувачі поставили абсолютно однаковий бал):
        if (stdDev == 0.0) {
            return currentRating.rating() == clusterObjectMean ? 0.0 : 1.0;
        }

        // 4. Розрахунок z-score відносно реального розкиду оцінок об'єкта
        double diff = Math.abs(currentRating.rating() - clusterObjectMean);
        double zScore = diff / stdDev;

        // 5. Нормалізація в діапазон [0.0, 1.0] за правилом 3 сигм
        double anomalyScore = Math.min(1.0, zScore / 3.0);

        return Math.max(0.0, Math.min(1.0, anomalyScore));
    }
}