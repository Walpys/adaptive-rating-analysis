package io.github.walpys.adaptiverating.model;

import io.github.walpys.adaptiverating.model.enums.AnalysisStatus;
import io.github.walpys.adaptiverating.model.enums.ResultClassification;
import lombok.Builder;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Builder(builderMethodName = "internalBuilder")
public record AnalysisResult(
        Rating rawRating,
        Double personalPercentile,
        String clusterId,
        Double personalAnomaly,
        Double temporalAnomaly,
        Double objectConsensusAnomaly,
        Double anomalyScore,
        AnalysisStatus status,
        ResultClassification classification,
        Map<String, Double> signalMap,
        String dominantSignal
) {
    public AnalysisResult {
        Objects.requireNonNull(rawRating, "rawRating must not be null");
        Objects.requireNonNull(status, "status must not be null");
        signalMap = signalMap != null ? Map.copyOf(signalMap) : Map.of();
    }

    public static AnalysisResultBuilder builder() {
        return new AnalysisResultBuilder();
    }

    public static class AnalysisResultBuilder {
        public AnalysisResultBuilder signalMap(Map<String, Double> signalMap) {
            this.signalMap = signalMap != null ? Map.copyOf(signalMap) : Map.of();
            return this;
        }
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

    public Optional<Double> getTemporalAnomaly() {
        return Optional.ofNullable(temporalAnomaly);
    }

    public Optional<Double> getAnomalyScore() {
        return Optional.ofNullable(anomalyScore);
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