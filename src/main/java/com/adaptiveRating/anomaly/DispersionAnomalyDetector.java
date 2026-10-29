package com.adaptiveRating.anomaly;

import com.adaptiveRating.model.UserProfile;

public class DispersionAnomalyDetector {

    private static final double MIN_NORMAL_STD_DEV = 0.5;
    private static final double MAX_NORMAL_STD_DEV = 3.2;
    private static final double MAX_POSSIBLE_STD_DEV = 4.55;

    public double calculateAnomaly(UserProfile profile) {
        if (profile.getTotalRatings() < 5) {
            return 0.0;
        }

        double stdDev = profile.getStandardDeviation();

        if (stdDev < MIN_NORMAL_STD_DEV) {
            double anomalyScore = (MIN_NORMAL_STD_DEV - stdDev) / MIN_NORMAL_STD_DEV;
            return Math.max(0.0, Math.min(1.0, anomalyScore));
        }

        if (stdDev > MAX_NORMAL_STD_DEV) {
            double anomalyScore = (stdDev - MAX_NORMAL_STD_DEV) / (MAX_POSSIBLE_STD_DEV - MAX_NORMAL_STD_DEV);
            return Math.max(0.0, Math.min(1.0, anomalyScore));
        }

        return 0.0;
    }
}