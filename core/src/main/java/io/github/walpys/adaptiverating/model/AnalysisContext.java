package io.github.walpys.adaptiverating.model;

import java.util.List;

public record AnalysisContext(
        Rating rating,
        List<Rating> userHistory,
        UserProfile profile,
        ClusterModel clusterModel
) {
    public AnalysisContext {
        userHistory = userHistory != null ? List.copyOf(userHistory) : List.of();
        profile = profile != null ? new UserProfile(profile) : null;
    }

    @Override
    public List<Rating> userHistory() {
        return List.copyOf(userHistory);
    }

    @Override
    public UserProfile profile() {
        return profile != null ? new UserProfile(profile) : null;
    }
}