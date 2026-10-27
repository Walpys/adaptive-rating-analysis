package com.adaptiveRating.anomaly;

import com.adaptiveRating.model.ClusterModel;
import com.adaptiveRating.model.UserProfile;
import com.adaptiveRating.model.UserVector;

public class ClusterAnomalyDetector {
    private static final double MAX_EUCLIDEAN_DISTANCE = Math.sqrt(2.0);

    public double calculateAnomaly(UserProfile profile, ClusterModel clusterModel) {
        if (profile.getClusterIndex() == null) {
            throw new IllegalStateException("UserProfile must have a clusterIndex assigned before anomaly detection!");
        }

        UserVector center = clusterModel.getClusterCenters().get(profile.getClusterIndex());
        double distance = calculateEuclideanDistance(profile.getUserVector(), center);
        double normalizedScore = distance / MAX_EUCLIDEAN_DISTANCE;

        return Math.max(0.0, Math.min(1.0, normalizedScore));
    }

    private double calculateEuclideanDistance(UserVector v1, UserVector v2) {
        double[] c1 = v1.components();
        double[] c2 = v2.components();
        double sum = 0.0;
        for (int i = 0; i < 10; i++) {
            double diff = c1[i] - c2[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
}
