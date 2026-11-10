package io.github.walpys.adaptiverating.anomaly;

import io.github.walpys.adaptiverating.model.Rating;
import io.github.walpys.adaptiverating.model.UserProfile;

public class PersonalAnomalyDetector {

    private static final double MIN_ALLOWED_STD_DEV = 0.5;
    private static final double EPSILON = 1e-6;

    public double calculateAnomaly(Rating currentRating, UserProfile userProfile) {
        double ratingValue = currentRating.rating();

        double mean = userProfile.getMean();
        double rawStdDev = userProfile.getStandardDeviation();

        double stdDev = Math.max(rawStdDev, MIN_ALLOWED_STD_DEV);

        if (stdDev < EPSILON) {
            return Math.abs(ratingValue - mean) < EPSILON ? 0.0 : 1.0;
        }

        double zScore = Math.abs(ratingValue - mean) / stdDev;

        double anomalyScore = Math.min(1.0, zScore / 3.0);

        return Math.max(0.0, Math.min(1.0, anomalyScore));
    }
}