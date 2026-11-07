package com.adaptiveRating.model;

import java.util.List;

public class ClusterModel {
    private final int k;
    private final List<UserVector> clusterCenters;
    private final List<Integer> clusterSizes;
    private final long trainingTimestamp;
    private final List<Double> clusterRadii;

    public ClusterModel(int k, List<UserVector> clusterCenters, List<Integer> clusterSizes, long trainingTimestamp, List<Double> clusterRadii) {
        this.k = k;
        this.clusterCenters = clusterCenters != null ? List.copyOf(clusterCenters) : List.of();
        this.clusterSizes = clusterSizes != null ? List.copyOf(clusterSizes) : List.of();
        this.trainingTimestamp = trainingTimestamp;
        this.clusterRadii = clusterRadii != null ? List.copyOf(clusterRadii) : List.of();
    }

    public ClusterModel(int k, List<UserVector> clusterCenters, long trainingTimestamp, List<Double> clusterRadii) {
        this(k, clusterCenters, null, trainingTimestamp, clusterRadii);
    }

    public int getK() {
        return k;
    }

    public List<UserVector> getClusterCenters() {
        return List.copyOf(clusterCenters);
    }

    public List<Integer> getClusterSizes() {
        return List.copyOf(clusterSizes);
    }

    public long getTrainingTimestamp() {
        return trainingTimestamp;
    }

    public List<Double> getClusterRadii() {
        return List.copyOf(clusterRadii);
    }
}