package com.adaptiveRating.model;

import com.adaptiveRating.model.enums.AnalysisStatus;
import com.adaptiveRating.model.enums.ResultClassification;

import java.util.Objects;
import java.util.Optional;

public record AnalysisResult(
        Rating rawRating,
        Double personalPercentile,
        String clusterId,
        Double personalAnomaly,
        Double clusterAnomaly,
        Double temporalAnomaly,
        Double anomalyScore,
        AnalysisStatus status,
        ResultClassification classification
) {
    public AnalysisResult {
        Objects.requireNonNull(rawRating, "rawRating must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public Optional<Double> getPersonalPercentile() {
        return Optional.ofNullable(personalPercentile);
    }

    public Optional<String> getClusterId() {
        return Optional.ofNullable(clusterId);
    }

    public Optional<Double> getPersonalAnomaly() {
        return Optional.ofNullable(personalAnomaly);
    }

    public Optional<Double> getClusterAnomaly() {
        return Optional.ofNullable(clusterAnomaly);
    }

    public Optional<Double> getTemporalAnomaly() {
        return Optional.ofNullable(temporalAnomaly);
    }

    public Optional<Double> getAnomalyScore() {
        return Optional.ofNullable(anomalyScore);
    }

    public Optional<ResultClassification> getClassification() {
        return Optional.ofNullable(classification);
    }
}