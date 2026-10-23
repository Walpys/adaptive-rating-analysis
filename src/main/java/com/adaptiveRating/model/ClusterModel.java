package com.adaptiveRating.model;

import com.adaptiveRating.exception.cluster.ClusterValidationException;

import java.util.List;
import java.util.Objects;

public class ClusterModel {
    private final int k;
    private final List<UserVector> clusterCenters;
    private final long trainingTimestamp;

    public ClusterModel(int k, List<UserVector> clusterCenters, long trainingTimestamp) {
        if (k <= 0) {
            throw new ClusterValidationException("K must be greater than 0");
        }
        Objects.requireNonNull(clusterCenters, "clusterCenters must not be null");
        if (clusterCenters.size() != k) {
            throw new ClusterValidationException("Number of cluster centers must match K");
        }
        this.k = k;
        this.clusterCenters = List.copyOf(clusterCenters);
        this.trainingTimestamp = trainingTimestamp;
    }

    public int getK() {
        return k;
    }

    public List<UserVector> getClusterCenters() {
        return clusterCenters;
    }

    public long getTrainingTimestamp() {
        return trainingTimestamp;
    }
}