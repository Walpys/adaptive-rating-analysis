package com.adaptiveRating.anomaly;

import com.adaptiveRating.model.ClusterModel;
import com.adaptiveRating.model.UserProfile;
import com.adaptiveRating.model.UserVector;

import java.util.List;

public class ClusterAnomalyDetector {

    private static final int MIN_CLUSTER_SIZE_PERCENT = 3;
    private static final int MIN_GLOBAL_USERS_FOR_CLUSTERING = 50; // Захисний поріг вибірки

    public double calculateAnomaly(UserProfile profile, ClusterModel clusterModel) {
        return calculateRawClusterScore(profile, clusterModel);
    }

    private double calculateRawClusterScore(UserProfile profile, ClusterModel clusterModel) {
        if (clusterModel == null || clusterModel.getClusterSizes() == null) {
            return 0.0;
        }

        // Захист для малих вибірок (тести, старт системи, мікро-бази)
        int totalUsers = clusterModel.getClusterSizes().stream().mapToInt(Integer::intValue).sum();
        if (totalUsers < MIN_GLOBAL_USERS_FOR_CLUSTERING) {
            // На малій вибірці кластеризація нестабільна, тому не караємо за віддаленість
            return 0.0;
        }

        if (profile.getClusterIndex() == null) {
            throw new IllegalStateException("UserProfile must have a clusterIndex assigned before anomaly detection!");
        }

        int clusterIdx = profile.getClusterIndex();
        List<UserVector> centers = clusterModel.getClusterCenters();

        if (clusterIdx < 0 || clusterIdx >= centers.size()) {
            return 0.5; // Безпечний fallback, якщо індекс вийшов за межі
        }

        UserVector assignedCenter = centers.get(clusterIdx);
        boolean isSmallCluster = isClusterTooSmall(clusterIdx, clusterModel, totalUsers);

        double distance;
        if (isSmallCluster && centers.size() > 1) {
            distance = calculateMinDistanceToOtherCenters(profile.getUserVector(), centers, clusterIdx);
            distance += 0.2;
        } else {
            distance = calculateEuclideanDistance(profile.getUserVector(), assignedCenter);
        }

        // Беремо реальний p95 радіус із моделі
        double dynamicMaxDistance = getClusterP95Radius(clusterIdx, clusterModel);

        // Запобігаємо діленню на нуль для вироджених кластерів
        if (dynamicMaxDistance < 0.1) {
            dynamicMaxDistance = 0.5;
        }

        // Нормалізація відносно p95
        double normalizedScore = distance / dynamicMaxDistance;
        return Math.max(0.0, Math.min(1.0, normalizedScore));
    }

    private double getClusterP95Radius(int clusterIdx, ClusterModel clusterModel) {
        List<Double> radii = clusterModel.getClusterRadii();
        if (radii != null && clusterIdx < radii.size() && radii.get(clusterIdx) > 0.0) {
            return radii.get(clusterIdx);
        }
        return 0.8;
    }

    private boolean isClusterTooSmall(int idx, ClusterModel m, int totalUsers) {
        List<Integer> sizes = m.getClusterSizes();
        if (sizes.isEmpty() || idx >= sizes.size()) return false;
        return sizes.get(idx) < Math.max(3, (int) Math.ceil(totalUsers * MIN_CLUSTER_SIZE_PERCENT / 100.0));
    }

    private double calculateMinDistanceToOtherCenters(UserVector userVector, List<UserVector> centers, int excludeIdx) {
        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < centers.size(); i++) {
            if (i == excludeIdx) continue;
            double dist = calculateEuclideanDistance(userVector, centers.get(i));
            if (dist < minDistance) {
                minDistance = dist;
            }
        }
        return minDistance;
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