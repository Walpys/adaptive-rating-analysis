package io.github.walpys.adaptiverating.anomaly;

import io.github.walpys.adaptiverating.model.Rating;
import io.github.walpys.adaptiverating.model.UserProfile;
import io.github.walpys.adaptiverating.profile.UserProfileBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.walpys.adaptiverating.TestFixtures.EPS;
import static io.github.walpys.adaptiverating.TestFixtures.history;
import static io.github.walpys.adaptiverating.TestFixtures.rating;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PersonalAnomalyDetectorTest {

    private final PersonalAnomalyDetector detector = new PersonalAnomalyDetector();
    private final UserProfileBuilder profileBuilder = new UserProfileBuilder();

    @Test
    @DisplayName("frequent rating dominating history yields low personal anomaly")
    void frequentRatingYieldsLowAnomaly() {
        List<Rating> ratings = history("u1", 5, 5, 5, 5, 5, 5, 5, 5, 5, 5);
        UserProfile profile = profileBuilder.build("u1", ratings);

        double score = detector.calculateAnomaly(rating("u1", 5.0), profile);

        assertThat(score).isCloseTo(0.0, within(EPS));
    }

    @Test
    @DisplayName("rare rating yields high anomaly score")
    void rareRatingYieldsHighAnomaly() {
        List<Rating> ratings = history("u1", 5, 5, 5, 5, 5, 5, 5, 5, 5, 10);
        UserProfile profile = profileBuilder.build("u1", ratings);

        double score = detector.calculateAnomaly(rating("u1", 10.0), profile);

        assertThat(score).isCloseTo(1.0, within(EPS));
        assertThat(score).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("unseen rating yields anomaly score of one")
    void unseenRatingYieldsOne() {
        List<Rating> ratings = history("u1", 3, 3, 3, 3);
        UserProfile profile = profileBuilder.build("u1", ratings);

        double score = detector.calculateAnomaly(rating("u1", 9.0), profile);

        assertThat(score).isCloseTo(1.0, within(EPS));
    }
}