package com.adaptiveRating.anomaly;

import com.adaptiveRating.model.Rating;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.adaptiveRating.TestFixtures.EPS;
import static com.adaptiveRating.TestFixtures.rating;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ObjectConsensusAnomalyDetectorTest {

    private final ObjectConsensusAnomalyDetector detector = new ObjectConsensusAnomalyDetector();

    @Test
    @DisplayName("порожній список оцінок об'єкта дає 0.0")
    void emptyObjectRatingsReturnsZero() {
        Rating current = rating("u1", "movie-1", 10.0);

        double score = detector.calculateAnomaly(current, List.of());

        assertThat(score).isEqualTo(0.0);
    }

    @Test
    @DisplayName("null-список оцінок об'єкта дає 0.0")
    void nullObjectRatingsReturnsZero() {
        Rating current = rating("u1", "movie-1", 10.0);

        double score = detector.calculateAnomaly(current, null);

        assertThat(score).isEqualTo(0.0);
    }

    @Test
    @DisplayName("оцінка, що збігається з clusterObjectMean, дає 0.0")
    void ratingEqualToClusterMeanReturnsZero() {
        Rating current = rating("u1", "movie-1", 7.0);
        List<Rating> clusterRatings = List.of(
                rating("u2", "movie-1", 7.0),
                rating("u3", "movie-1", 7.0),
                rating("u4", "movie-1", 7.0)
        );

        double score = detector.calculateAnomaly(current, clusterRatings);

        assertThat(score).isCloseTo(0.0, within(EPS));
    }

    @Test
    @DisplayName("відхилення від середнього консенсусу нормалізується як |r - mean| / 9")
    void deviationFromClusterObjectMeanIsNormalizedByNine() {
        Rating current = rating("u1", "movie-1", 10.0);
        List<Rating> clusterRatings = List.of(
                rating("u2", "movie-1", 4.0),
                rating("u3", "movie-1", 6.0)
        );
        double clusterObjectMean = 5.0;
        double expected = Math.abs(10.0 - clusterObjectMean) / 9.0;

        double score = detector.calculateAnomaly(current, clusterRatings);

        assertThat(score).isCloseTo(expected, within(EPS));
        assertThat(score).isCloseTo(5.0 / 9.0, within(EPS));
    }

    @Test
    @DisplayName("максимальна різниця 9 балів на шкалі [1, 10] дає аномалію 1.0")
    void maxNinePointGapYieldsOne() {
        Rating current = rating("u1", "movie-1", 10.0);
        List<Rating> clusterRatings = List.of(
                rating("u2", "movie-1", 1.0),
                rating("u3", "movie-1", 1.0)
        );

        double score = detector.calculateAnomaly(current, clusterRatings);

        assertThat(score).isCloseTo(1.0, within(EPS));
    }

    @Test
    @DisplayName("симетричний максимум: оцінка 1.0 проти середнього 10.0 теж дає 1.0")
    void minAgainstMaxMeanYieldsOne() {
        Rating current = rating("u1", "movie-1", 1.0);
        List<Rating> clusterRatings = List.of(rating("u2", "movie-1", 10.0));

        double score = detector.calculateAnomaly(current, clusterRatings);

        assertThat(score).isCloseTo(1.0, within(EPS));
    }

    @Test
    @DisplayName("аномалія завжди лежить у діапазоні [0.0, 1.0]")
    void scoreIsClampedToUnitInterval() {
        Rating current = rating("u1", "movie-1", 8.5);
        List<Rating> clusterRatings = List.of(
                rating("u2", "movie-1", 1.0),
                rating("u3", "movie-1", 3.0),
                rating("u4", "movie-1", 9.0)
        );

        double score = detector.calculateAnomaly(current, clusterRatings);

        assertThat(score).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("порожня immutable-колекція обробляється так само, як порожній список")
    void emptyImmutableCollectionReturnsZero() {
        double score = detector.calculateAnomaly(rating("u1", 5.0), Collections.emptyList());

        assertThat(score).isZero();
    }
}
