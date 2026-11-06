package com.adaptiveRating.anomaly;

import com.adaptiveRating.model.UserProfile;
import com.adaptiveRating.model.UserVector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.adaptiveRating.TestFixtures.EPS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DispersionAnomalyDetectorTest {

    private final DispersionAnomalyDetector detector = new DispersionAnomalyDetector();

    @Test
    @DisplayName("profile with less than 5 ratings returns zero anomaly score")
    void profileWithLessThanFiveRatingsYieldsZero() {
        UserProfile profile = createUserProfile(4, 8.0, 0.0);

        double score = detector.calculateAnomaly(profile);

        assertThat(score).isZero();
    }

    @Test
    @DisplayName("normal standard deviation yields zero anomaly score")
    void normalStdDevYieldsZero() {
        UserProfile profile = createUserProfile(10, 7.5, 1.2);

        double score = detector.calculateAnomaly(profile);

        assertThat(score).isZero();
    }

    @Test
    @DisplayName("low standard deviation scales score smoothly based on min limit")
    void lowStdDevScalesScoreSmoothly() {
        UserProfile profile = createUserProfile(10, 8.0, 0.3);

        double score = detector.calculateAnomaly(profile);

        assertThat(score).isGreaterThan(0.0);
        assertThat(score).isCloseTo(0.5, within(EPS));
    }

    @Test
    @DisplayName("zero standard deviation yields maximum score for low dispersion")
    void zeroStdDevYieldsOne() {
        UserProfile profile = createUserProfile(10, 8.0, 0.0);

        double score = detector.calculateAnomaly(profile);

        assertThat(score).isCloseTo(1.0, within(EPS));
    }

    @Test
    @DisplayName("extremely high standard deviation is capped at one")
    void extremelyHighStdDevCappedAtOne() {
        UserProfile profile = createUserProfile(10, 5.0, 5.0);

        double score = detector.calculateAnomaly(profile);

        assertThat(score).isCloseTo(1.0, within(EPS));
    }

    private UserProfile createUserProfile(int totalRatings, double mean, double stdDev) {
        return new UserProfile(
                "user-1",
                totalRatings,
                Map.of(5, totalRatings),
                new UserVector(new double[]{0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1}),
                mean,
                mean,
                stdDev,
                1.0,
                10.0,
                null
        );
    }
}