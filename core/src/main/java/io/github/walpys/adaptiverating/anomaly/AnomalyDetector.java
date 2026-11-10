package io.github.walpys.adaptiverating.anomaly;

import io.github.walpys.adaptiverating.model.Rating;

import java.util.List;

public interface AnomalyDetector {
    double calculateAnomaly(Rating rating, List<Rating> history);
}

