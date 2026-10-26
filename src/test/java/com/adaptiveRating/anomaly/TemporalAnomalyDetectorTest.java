package com.adaptiveRating.anomaly;

import com.adaptiveRating.model.Rating;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.adaptiveRating.TestFixtures.EPS;
import static com.adaptiveRating.TestFixtures.T0;
import static com.adaptiveRating.TestFixtures.rating;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TemporalAnomalyDetectorTest {

    @Test
    @DisplayName("кількість оцінок у вікні не вище норми дає 0.0")
    void withinNormalCountReturnsZero() {
        TemporalAnomalyDetector detector = new TemporalAnomalyDetector(Duration.ofHours(1), 5);
        Rating current = rating("u1", "obj-1", 5.0, T0);
        List<Rating> history = ratingsInWindow("u1", T0, 3);

        double score = detector.calculateAnomaly(current, history);

        assertThat(score).isZero();
    }

    @Test
    @DisplayName("надлишок оцінок нормалізується як excess / (maxNormalCount * 2) і затискається до 1.0")
    void excessRatingsIncreaseScoreUpToOne() {
        TemporalAnomalyDetector detector = new TemporalAnomalyDetector(Duration.ofHours(1), 2);
        Rating current = rating("u1", "obj-1", 5.0, T0);
        List<Rating> history = ratingsInWindow("u1", T0, 6);

        double score = detector.calculateAnomaly(current, history);

        assertThat(score).isCloseTo(1.0, within(EPS));
    }

    @Test
    @DisplayName("оцінки поза часовим вікном не враховуються")
    void ratingsOutsideWindowAreIgnored() {
        TemporalAnomalyDetector detector = new TemporalAnomalyDetector(Duration.ofHours(1), 2);
        Rating current = rating("u1", "obj-1", 5.0, T0);
        List<Rating> history = List.of(
                rating("u1", "obj-1", 5.0, T0.minus(Duration.ofHours(5))),
                rating("u1", "obj-1", 5.0, T0.minus(Duration.ofDays(1)))
        );

        double score = detector.calculateAnomaly(current, history);

        assertThat(score).isZero();
    }

    private static List<Rating> ratingsInWindow(String userId, Instant current, int count) {
        List<Rating> ratings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ratings.add(rating(userId, "obj-1", 5.0, current.minusSeconds(i)));
        }
        return ratings;
    }
}
