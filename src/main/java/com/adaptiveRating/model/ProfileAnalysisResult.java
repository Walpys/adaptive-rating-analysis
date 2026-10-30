package com.adaptiveRating.model;

import com.adaptiveRating.model.enums.AnalysisStatus;
import com.adaptiveRating.model.enums.ResultClassification;

import java.util.Objects;
import java.util.Optional;

public record ProfileAnalysisResult(
        String userId,
        int totalRatings,
        Double meanRating,
        Double standardDeviation,
        String clusterId,
        Double clusterAnomaly,
        Double dispersionAnomaly,
        Double temporalAnomaly,
        Double overallAnomalyScore,
        AnalysisStatus status,
        ResultClassification classification
) {
    public ProfileAnalysisResult {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public Optional<Double> getClusterAnomaly() {
        return Optional.ofNullable(clusterAnomaly);
    }

    public Optional<Double> getDispersionAnomaly() {
        return Optional.ofNullable(dispersionAnomaly);
    }

    public Optional<Double> getOverallAnomalyScore() {
        return Optional.ofNullable(overallAnomalyScore);
    }

    public Optional<ResultClassification> getClassification() {
        return Optional.ofNullable(classification);
    }
}