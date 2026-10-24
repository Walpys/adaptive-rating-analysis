package com.adaptiveRating.anomaly;

import com.adaptiveRating.model.Rating;

import java.util.List;

public class ObjectConsensusAnomalyDetector {

    public double calculateAnomaly(Rating currentRating, List<Rating> objectRatingsInCluster) {
        if (objectRatingsInCluster == null || objectRatingsInCluster.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;
        for (Rating r : objectRatingsInCluster) {
            sum += r.rating();
        }
        double clusterObjectMean = sum / objectRatingsInCluster.size();

        double diff = Math.abs(currentRating.rating() - clusterObjectMean);

        double anomalyScore = diff / 9.0;

        return Math.max(0.0, Math.min(1.0, anomalyScore));
    }
}