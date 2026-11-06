package com.adaptiveRating.model;

import lombok.Getter;

import java.util.List;

@Getter
public class ClusterModel {
    private final int k;
    private final List<UserVector> clusterCenters;
    private final List<Integer> clusterSizes;
    private final long trainingTimestamp;
    private final List<Double> clusterRadii;

    public ClusterModel(int k, List<UserVector> clusterCenters, List<Integer> clusterSizes, long trainingTimestamp, List<Double> clusterRadii) {
        this.k = k;
        this.clusterCenters = List.copyOf(clusterCenters);
        this.clusterSizes = clusterSizes != null ? List.copyOf(clusterSizes) : List.of();
        this.trainingTimestamp = trainingTimestamp;
        this.clusterRadii = clusterRadii;
    }

    public ClusterModel(int k, List<UserVector> clusterCenters, long trainingTimestamp, List<Double> clusterRadii) {
        this(k, clusterCenters, null, trainingTimestamp, clusterRadii);
    }
}