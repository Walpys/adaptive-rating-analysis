package com.adaptiveRating.anomaly;

import com.adaptiveRating.model.Rating;
import com.adaptiveRating.model.UserProfile;

import java.util.List;

public class DispersionAnomalyDetector {

    private static final double MIN_NORMAL_STD_DEV = 0.6;
    private static final double MAX_NORMAL_STD_DEV = 3.2;
    private static final double MAX_POSSIBLE_STD_DEV = 4.55;

    public double calculateProfileAnomaly(List<Rating> history) {
        if (history == null || history.size() < 5) {
            return 0.0;
        }

        // Рахуємо середнє значення за всю історію
        double sum = 0.0;
        for (Rating r : history) {
            sum += r.rating();
        }
        double totalSum = sum;
        int n = history.size();

        double sumSquaredDiff = 0.0;

        // Використовуємо підхід leave-one-out або загальну дисперсію профілю
        for (Rating r : history) {
            double ratingVal = r.rating();
            // Середнє без урахування поточної оцінки (leave-one-out mean)
            double leaveOneOutMean = (totalSum - ratingVal) / (n - 1);

            // Відхилення поточної оцінки від еталону решти профілю
            double diff = ratingVal - leaveOneOutMean;
            sumSquaredDiff += diff * diff;
        }

        double stdDev = Math.sqrt(sumSquaredDiff / n);

        if (stdDev < MIN_NORMAL_STD_DEV) {
            double anomalyScore = (MIN_NORMAL_STD_DEV - stdDev) / MIN_NORMAL_STD_DEV;
            return Math.max(0.0, Math.min(1.0, anomalyScore)); //зауваження
        }

        if (stdDev > MAX_NORMAL_STD_DEV) {
            double anomalyScore = (stdDev - MAX_NORMAL_STD_DEV) / (MAX_POSSIBLE_STD_DEV - MAX_NORMAL_STD_DEV);
            return Math.max(0.0, Math.min(1.0, anomalyScore));
        }

        return 0.0;
    }
}