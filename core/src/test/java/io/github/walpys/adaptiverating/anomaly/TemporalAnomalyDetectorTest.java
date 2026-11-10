package io.github.walpys.adaptiverating.anomaly;

import io.github.walpys.adaptiverating.config.WindowRule;
import io.github.walpys.adaptiverating.model.Rating;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static io.github.walpys.adaptiverating.TestFixtures.EPS;
import static io.github.walpys.adaptiverating.TestFixtures.T0;
import static io.github.walpys.adaptiverating.TestFixtures.rating;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TemporalAnomalyDetectorTest {

    @Test
    @DisplayName("rating count in window within normal limits returns zero")
    void withinNormalCountReturnsZero() {
        TemporalAnomalyDetector detector = new TemporalAnomalyDetector(List.of(
                new WindowRule(Duration.ofHours(1), 5, 20)
        ));
        Rating current = rating("u1", "obj-1", 5.0, T0);
        List<Rating> history = ratingsInWindow("u1", T0, 3);

        double score = detector.calculateAnomaly(current, history);

        assertThat(score).isZero();
    }

    @Test
    @DisplayName("excess ratings increase score according to logarithmic rule curve")
    void excessRatingsIncreaseScoreUpToOne() {
        int maxNormal = 2;
        int saturationCount = 10;
        int count = 6;

        TemporalAnomalyDetector detector = new TemporalAnomalyDetector(List.of(
                new WindowRule(Duration.ofHours(1), maxNormal, saturationCount)
        ));
        Rating current = rating("u1", "obj-1", 5.0, T0);
        List<Rating> history = ratingsInWindow("u1", T0, count);

        double score = detector.calculateAnomaly(current, history);

        double expectedScore = Math.log((double) count / maxNormal)
                / Math.log((double) saturationCount / maxNormal);

        assertThat(score).isCloseTo(expectedScore, within(EPS));
    }

    @Test
    @DisplayName("ratings outside time window are ignored")
    void ratingsOutsideWindowAreIgnored() {
        TemporalAnomalyDetector detector = new TemporalAnomalyDetector(List.of(
                new WindowRule(Duration.ofHours(1), 2, 10)
        ));
        Rating current = rating("u1", "obj-1", 5.0, T0);
        List<Rating> history = List.of(
                rating("u1", "obj-1", 5.0, T0.minus(Duration.ofHours(5))),
                rating("u1", "obj-1", 5.0, T0.minus(Duration.ofDays(1)))
        );

        double score = detector.calculateAnomaly(current, history);

        assertThat(score).isZero();
    }

    @Test
    @DisplayName("updatedAt takes precedence over timestamp when calculating effective time window")
    void updatedAtTakesPrecedenceInWindowCalculation() {
        int maxNormal = 2;
        int saturationCount = 10;

        double effectiveWeightSum = 3.0;

        TemporalAnomalyDetector detector = new TemporalAnomalyDetector(List.of(
                new WindowRule(Duration.ofHours(1), maxNormal, saturationCount)
        ));
        Instant oldTime = T0.minus(Duration.ofDays(10));

        Rating current = new Rating("u1", "obj-1", 5.0, oldTime, T0);
        List<Rating> history = List.of(
                current,
                new Rating("u1", "obj-2", 6.0, oldTime, T0.minusSeconds(10)),
                new Rating("u1", "obj-3", 7.0, oldTime, T0.minusSeconds(20)),
                new Rating("u1", "obj-4", 8.0, oldTime, T0.minusSeconds(30)),
                new Rating("u1", "obj-5", 9.0, oldTime, T0.minusSeconds(40)),
                new Rating("u1", "obj-6", 10.0, oldTime, T0.minusSeconds(50))
        );

        double score = detector.calculateAnomaly(current, history);

        double expectedScore = Math.log(effectiveWeightSum / maxNormal)
                / Math.log((double) saturationCount / maxNormal);

        assertThat(score).isCloseTo(expectedScore, within(EPS));
    }

    @Test
    @DisplayName("calculateProfileAnomaly with empty or null history returns zero")
    void calculateProfileAnomalyWithNullOrEmptyYieldsZero() {
        TemporalAnomalyDetector detector = TemporalAnomalyDetector.withDefaults();

        assertThat(detector.calculateProfileAnomaly(null)).isZero();
        assertThat(detector.calculateProfileAnomaly(List.of())).isZero();
    }

    @Test
    @DisplayName("calculateProfileAnomaly evaluates window around the latest effective rating in history")
    void calculateProfileAnomalyUsesLatestRatingInHistory() {
        int maxNormal = 2;
        int saturationCount = 10;
        int count = 6;

        TemporalAnomalyDetector detector = new TemporalAnomalyDetector(List.of(
                new WindowRule(Duration.ofHours(1), maxNormal, saturationCount)
        ));

        List<Rating> history = new ArrayList<>(ratingsInWindow("u1", T0, count));
        history.add(0, rating("u1", "obj-0", 5.0, T0.minus(Duration.ofDays(5))));

        Double score = detector.calculateProfileAnomaly(history);

        assertThat(score).isNotNull();
        double expectedScore = Math.log((double) count / maxNormal)
                / Math.log((double) saturationCount / maxNormal);

        assertThat(score).isCloseTo(expectedScore, within(EPS));
    }

    private static List<Rating> ratingsInWindow(String userId, Instant current, int count) {
        List<Rating> ratings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ratings.add(rating(userId, "obj-1", 5.0, current.minusSeconds(i)));
        }
        return ratings;
    }
}