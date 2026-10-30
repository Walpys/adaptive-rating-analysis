package com.adaptiveRating.anomaly;

import com.adaptiveRating.model.Rating;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.adaptiveRating.TestFixtures.EPS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DispersionAnomalyDetectorTest {

    private final DispersionAnomalyDetector detector = new DispersionAnomalyDetector();

    @Test
    @DisplayName("profile with less than 5 ratings returns zero anomaly score")
    void profileWithLessThanFiveRatingsYieldsZero() {
        List<Rating> history = createRatingsWithStdDev(4, 5.0, 0.0);

        double score = detector.calculateProfileAnomaly(history);

        assertThat(score).isZero();
    }

    @Test
    @DisplayName("normal standard deviation yields zero anomaly score")
    void normalStdDevYieldsZero() {
        List<Rating> history = List.of(
                createRating(7.0),
                createRating(8.0),
                createRating(8.5),
                createRating(9.0),
                createRating(8.0)
        );

        double score = detector.calculateProfileAnomaly(history);

        assertThat(score).isZero();
    }

    @Test
    @DisplayName("low standard deviation scales score smoothly based on min limit")
    void lowStdDevScalesScoreSmoothly() {
        List<Rating> history = List.of(
                createRating(8.0),
                createRating(8.1),
                createRating(8.0),
                createRating(8.05),
                createRating(8.0)
        );

        double score = detector.calculateProfileAnomaly(history);

        assertThat(score).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("zero standard deviation yields maximum score for low dispersion")
    void zeroStdDevYieldsOne() {
        List<Rating> history = List.of(
                createRating(8.0),
                createRating(8.0),
                createRating(8.0),
                createRating(8.0),
                createRating(8.0)
        );

        double score = detector.calculateProfileAnomaly(history);

        assertThat(score).isCloseTo(1.0, within(EPS));
    }

    @Test
    @DisplayName("extremely high standard deviation is capped at one")
    void extremelyHighStdDevCappedAtOne() {
        List<Rating> history = List.of(
                createRating(1.0),
                createRating(10.0),
                createRating(1.0),
                createRating(10.0),
                createRating(5.0)
        );

        double score = detector.calculateProfileAnomaly(history);

        assertThat(score).isCloseTo(1.0, within(EPS));
    }

    private Rating createRating(double value) {
        return new Rating("user-1", "obj-1", value, Instant.now());
    }

    private List<Rating> createRatingsWithStdDev(int count, double baseValue, double deviation) {
        List<Rating> ratings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double val = baseValue + (i % 2 == 0 ? deviation : -deviation);
            ratings.add(createRating(Math.max(1.0, Math.min(10.0, val))));
        }
        return ratings;
    }
}