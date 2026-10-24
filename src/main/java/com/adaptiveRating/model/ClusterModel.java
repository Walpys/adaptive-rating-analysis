package com.adaptiveRating.model;

import lombok.Getter;

import java.util.List;

@Getter
public class ClusterModel {
    private final int k;
    private final List<UserVector> clusterCenters;
    private final long trainingTimestamp;

    public ClusterModel(int k, List<UserVector> clusterCenters, long trainingTimestamp) {
        this.k = k;
        this.clusterCenters = List.copyOf(clusterCenters);
        this.trainingTimestamp = trainingTimestamp;
    }
}
