package com.adaptiveRating.anomaly;

import com.adaptiveRating.model.Rating;
import com.adaptiveRating.model.UserProfile;

public class PersonalAnomalyDetector {

    // Захисна нижня межа для стандартного відхилення, щоб уникнути штучного перебору
    // для користувачів із дуже вузьким, специфічним діапазоном оцінок (наприклад, σ ≈ 0.1–0.17)
    private static final double MIN_ALLOWED_STD_DEV = 0.5;
    private static final double EPSILON = 1e-6;

    public double calculateAnomaly(Rating currentRating, UserProfile userProfile) {
        double ratingValue = currentRating.rating();

        double mean = userProfile.getMean();
        double rawStdDev = userProfile.getStandardDeviation();

        // Застосовуємо нижню межу та безпечну перевірку на нуль через епсилон
        double stdDev = Math.max(rawStdDev, MIN_ALLOWED_STD_DEV);

        if (stdDev < EPSILON) {
            return Math.abs(ratingValue - mean) < EPSILON ? 0.0 : 1.0;
        }

        double zScore = Math.abs(ratingValue - mean) / stdDev;

        // Нормалізуємо zScore (наприклад, z = 3 вважається максимальним порогом аномалії)
        double anomalyScore = Math.min(1.0, zScore / 3.0);

        return Math.max(0.0, Math.min(1.0, anomalyScore));
    }
}