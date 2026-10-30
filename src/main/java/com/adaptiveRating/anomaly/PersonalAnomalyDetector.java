package com.adaptiveRating.anomaly;

import com.adaptiveRating.model.Rating;
import com.adaptiveRating.model.UserProfile;

public class PersonalAnomalyDetector {

    public double calculateAnomaly(Rating currentRating, UserProfile userProfile) {
        double ratingValue = currentRating.rating();

        double mean = userProfile.getMean();
        double stdDev = userProfile.getStandardDeviation();

        if (stdDev == 0.0) {
            return ratingValue == mean ? 0.0 : 1.0;
        }

        double zScore = Math.abs(ratingValue - mean) / stdDev;

        double anomalyScore = Math.min(1.0, zScore / 3.0);

        return Math.max(0.0, Math.min(1.0, anomalyScore));
    }
}