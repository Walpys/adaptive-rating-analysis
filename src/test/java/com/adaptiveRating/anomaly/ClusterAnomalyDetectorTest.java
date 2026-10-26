package com.adaptiveRating.anomaly;

import com.adaptiveRating.model.ClusterModel;
import com.adaptiveRating.model.UserVector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.adaptiveRating.TestFixtures.EPS;
import static com.adaptiveRating.TestFixtures.unitAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ClusterAnomalyDetectorTest {

    private final ClusterAnomalyDetector detector = new ClusterAnomalyDetector();

    @Test
    @DisplayName("вектор у центрі кластера дає аномалію 0.0")
    void vectorAtCenterYieldsZero() {
        UserVector center = unitAt(5);
        ClusterModel model = new ClusterModel(1, List.of(center), 0L);

        double score = detector.calculateAnomaly(center, model, 0);

        assertThat(score).isCloseTo(0.0, within(EPS));
    }

    @Test
    @DisplayName("максимальна евклідова відстань sqrt(2) нормалізується до 1.0")
    void orthogonalPeaksYieldOne() {
        UserVector center = unitAt(1);
        UserVector far = unitAt(10);
        ClusterModel model = new ClusterModel(1, List.of(center), 0L);

        double score = detector.calculateAnomaly(far, model, 0);

        assertThat(score).isCloseTo(1.0, within(EPS));
        assertThat(score).isBetween(0.0, 1.0);
    }
}
