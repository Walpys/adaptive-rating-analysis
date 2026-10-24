package com.adaptiveRating.anomaly;

import com.adaptiveRating.model.Rating;
import com.adaptiveRating.model.UserProfile;

public class PersonalAnomalyDetector {

    public double calculateAnomaly(Rating currentRating, UserProfile userProfile) {
        double ratingValue = currentRating.rating();
        int totalRatings = userProfile.totalRatings();
        int countOfValue = userProfile.ratingCounts().getOrDefault(ratingValue, 0);

        double frequency = (double) countOfValue / totalRatings;
        double anomalyScore = 1.0 - frequency;

        return Math.max(0.0, Math.min(1.0, anomalyScore));
    }
}
