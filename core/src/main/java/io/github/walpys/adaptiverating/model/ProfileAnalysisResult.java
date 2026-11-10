package io.github.walpys.adaptiverating.model;

import io.github.walpys.adaptiverating.model.enums.AnalysisStatus;
import io.github.walpys.adaptiverating.model.enums.ResultClassification;
import lombok.Builder;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Builder(builderMethodName = "internalBuilder")
public record ProfileAnalysisResult(
        String userId,
        int totalRatings,
        Double meanRating,
        Double standardDeviation,
        String clusterId,
        Double clusterAnomaly,
        Double dispersionAnomaly,
        Double temporalAnomaly,
        Double objectConsensusAnomaly,
        Double overallAnomalyScore,
        AnalysisStatus status,
        ResultClassification classification,
        Map<String, Double> signalMap,
        String dominantSignal
) {
    public ProfileAnalysisResult {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        signalMap = signalMap != null ? Map.copyOf(signalMap) : Map.of();
    }

    public static ProfileAnalysisResultBuilder builder() {
        return new ProfileAnalysisResultBuilder();
    }

    public static class ProfileAnalysisResultBuilder {
        public ProfileAnalysisResultBuilder signalMap(Map<String, Double> signalMap) {
            this.signalMap = signalMap != null ? Map.copyOf(signalMap) : Map.of();
            return this;
        }
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

    public Optional<Map<String, Double>> getSignalMap() {
        return signalMap != null ? Optional.of(Map.copyOf(signalMap)) : Optional.empty();
    }

    public Optional<String> getDominantSignal() {
        return Optional.ofNullable(dominantSignal);
    }
}