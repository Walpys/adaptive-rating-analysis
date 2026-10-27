package com.adaptiveRating.model;

import java.util.List;

public record AnalysisContext(
        Rating rating,
        List<Rating> userHistory,
        UserProfile profile,
        ClusterModel clusterModel,
        List<Rating> objectRatingsInCluster
) {
    public AnalysisContext {
        userHistory = userHistory != null ? List.copyOf(userHistory) : List.of();
        objectRatingsInCluster = objectRatingsInCluster != null ? List.copyOf(objectRatingsInCluster) : List.of();
        profile = profile != null ? new UserProfile(profile) : null;
    }

    @Override
    public List<Rating> userHistory() {
        return List.copyOf(userHistory);
    }

    @Override
    public List<Rating> objectRatingsInCluster() {
        return List.copyOf(objectRatingsInCluster);
    }

    @Override
    public UserProfile profile() {
        return profile != null ? new UserProfile(profile) : null;
    }
}