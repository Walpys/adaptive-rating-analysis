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
    @DisplayName("empty object ratings list returns zero")
    void emptyObjectRatingsReturnsZero() {
        Rating current = rating("u1", "movie-1", 10.0);

        double score = detector.calculateAnomaly(current, List.of());

        assertThat(score).isEqualTo(0.0);
    }

    @Test
    @DisplayName("null object ratings list returns zero")
    void nullObjectRatingsReturnsZero() {
        Rating current = rating("u1", "movie-1", 10.0);

        double score = detector.calculateAnomaly(current, null);

        assertThat(score).isEqualTo(0.0);
    }

    @Test
    @DisplayName("less than 5 other ratings returns zero score due to MIN_OTHER_RATINGS constraint")
    void lessThanMinOtherRatingsReturnsZero() {
        Rating current = rating("u1", "movie-1", 10.0);
        // Лише 4 інші оцінки (менше за MIN_OTHER_RATINGS = 5)
        List<Rating> clusterRatings = List.of(
                rating("u2", "movie-1", 1.0),
                rating("u3", "movie-1", 1.0),
                rating("u4", "movie-1", 1.0),
                rating("u5", "movie-1", 1.0)
        );

        double score = detector.calculateAnomaly(current, clusterRatings);

        assertThat(score).isZero();
    }

    @Test
    @DisplayName("rating equal to cluster object mean returns zero")
    void ratingEqualToClusterMeanReturnsZero() {
        Rating current = rating("u1", "movie-1", 7.0);
        List<Rating> clusterRatings = List.of(
                rating("u2", "movie-1", 7.0),
                rating("u3", "movie-1", 7.0),
                rating("u4", "movie-1", 7.0),
                rating("u5", "movie-1", 7.0),
                rating("u6", "movie-1", 7.0)
        );

        double score = detector.calculateAnomaly(current, clusterRatings);

        assertThat(score).isCloseTo(0.0, within(EPS));
    }

    @Test
    @DisplayName("ratings from the same user are excluded from other ratings count and statistics")
    void ratingFromSameUserIsExcluded() {
        Rating current = rating("u1", "movie-1", 10.0);
        // 5 оцінок у списку, але одна з них від u1 -> залишається лише 4 інші оцінки
        List<Rating> clusterRatings = List.of(
                current,
                rating("u2", "movie-1", 1.0),
                rating("u3", "movie-1", 1.0),
                rating("u4", "movie-1", 1.0),
                rating("u5", "movie-1", 1.0)
        );

        double score = detector.calculateAnomaly(current, clusterRatings);

        assertThat(score).isZero();
    }

    @Test
    @DisplayName("deviation from cluster mean is normalized by object stdDev z-score")
    void deviationFromClusterObjectMeanIsNormalizedByStdDev() {
        Rating current = rating("u1", "movie-1", 9.0);
        // 5 інших оцінок: mean = 5.0, ss = 2, std = sqrt(2/4) = 0.707 -> Math.max(0.707, 1.0) = 1.0
        // z = |9 - 5| / 1.0 = 4.0 -> score = (4 - 1)/2 = 1.0 (clamped)
        List<Rating> clusterRatings = List.of(
                rating("u2", "movie-1", 4.0),
                rating("u3", "movie-1", 6.0),
                rating("u4", "movie-1", 5.0),
                rating("u5", "movie-1", 5.0),
                rating("u6", "movie-1", 5.0)
        );

        double score = detector.calculateAnomaly(current, clusterRatings);

        assertThat(score).isGreaterThan(0.0);
        assertThat(score).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("maximum gap yields anomaly score of one")
    void maxGapYieldsOne() {
        Rating current = rating("u1", "movie-1", 10.0);
        List<Rating> clusterRatings = List.of(
                rating("u2", "movie-1", 1.0),
                rating("u3", "movie-1", 1.0),
                rating("u4", "movie-1", 1.0),
                rating("u5", "movie-1", 1.0),
                rating("u6", "movie-1", 1.0)
        );

        double score = detector.calculateAnomaly(current, clusterRatings);

        assertThat(score).isCloseTo(1.0, within(EPS));
    }

    @Test
    @DisplayName("symmetric maximum: rating of one against mean of ten yields one")
    void minAgainstMaxMeanYieldsOne() {
        Rating current = rating("u1", "movie-1", 1.0);
        List<Rating> clusterRatings = List.of(
                rating("u2", "movie-1", 10.0),
                rating("u3", "movie-1", 10.0),
                rating("u4", "movie-1", 10.0),
                rating("u5", "movie-1", 10.0),
                rating("u6", "movie-1", 10.0)
        );

        double score = detector.calculateAnomaly(current, clusterRatings);

        assertThat(score).isCloseTo(1.0, within(EPS));
    }

    @Test
    @DisplayName("score is always clamped to unit interval [0, 1]")
    void scoreIsClampedToUnitInterval() {
        Rating current = rating("u1", "movie-1", 8.5);
        List<Rating> clusterRatings = List.of(
                rating("u2", "movie-1", 1.0),
                rating("u3", "movie-1", 3.0),
                rating("u4", "movie-1", 9.0),
                rating("u5", "movie-1", 2.0),
                rating("u6", "movie-1", 4.0)
        );

        double score = detector.calculateAnomaly(current, clusterRatings);

        assertThat(score).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("empty immutable collection is handled same as empty list")
    void emptyImmutableCollectionReturnsZero() {
        double score = detector.calculateAnomaly(rating("u1", "movie-1", 5.0), Collections.emptyList());

        assertThat(score).isZero();
    }
}