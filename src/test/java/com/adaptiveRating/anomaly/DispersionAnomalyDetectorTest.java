package com.adaptiveRating.anomaly;

import com.adaptiveRating.model.UserProfile;
import com.adaptiveRating.model.UserVector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.adaptiveRating.TestFixtures.EPS;
import static com.adaptiveRating.TestFixtures.unitAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DispersionAnomalyDetectorTest {

    private final DispersionAnomalyDetector detector = new DispersionAnomalyDetector();

    @Test
    @DisplayName("profile with less than 5 ratings returns zero anomaly score")
    void profileWithLessThanFiveRatingsYieldsZero() {
        UserProfile profile = createProfile(4, 0.0);

        double score = detector.calculateAnomaly(profile);

        assertThat(score).isZero();
    }

    @Test
    @DisplayName("normal standard deviation yields zero anomaly score")
    void normalStdDevYieldsZero() {
        UserProfile profile = createProfile(10, 1.5);

        double score = detector.calculateAnomaly(profile);

        assertThat(score).isZero();
    }

    @Test
    @DisplayName("low standard deviation scales score smoothly based on min limit")
    void lowStdDevScalesScoreSmoothly() {

        UserProfile profile = createProfile(10, 0.25);

        double score = detector.calculateAnomaly(profile);

        assertThat(score).isCloseTo(0.5, within(EPS));
    }

    @Test
    @DisplayName("zero standard deviation yields maximum score for low dispersion")
    void zeroStdDevYieldsOne() {
        UserProfile profile = createProfile(10, 0.0);

        double score = detector.calculateAnomaly(profile);

        assertThat(score).isCloseTo(1.0, within(EPS));
    }

    @Test
    @DisplayName("high standard deviation scales score smoothly towards max limit")
    void highStdDevScalesScoreSmoothly() {

        UserProfile profile = createProfile(10, 3.875);

        double score = detector.calculateAnomaly(profile);

        assertThat(score).isCloseTo(0.5, within(EPS));
    }

    @Test
    @DisplayName("extremely high standard deviation is capped at one")
    void extremelyHighStdDevCappedAtOne() {
        UserProfile profile = createProfile(10, 5.0);

        double score = detector.calculateAnomaly(profile);

        assertThat(score).isCloseTo(1.0, within(EPS));
    }

    private UserProfile createProfile(int totalRatings, double stdDev) {
        UserVector dummyVector = unitAt(1);
        return new UserProfile(
                "user-1",
                totalRatings,
                Map.of(),
                dummyVector,
                5.0,
                5.0,
                stdDev,
                1.0,
                10.0,
                0
        );
    }
}