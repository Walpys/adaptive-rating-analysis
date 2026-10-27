package com.adaptiveRating.percentile;

import com.adaptiveRating.model.Rating;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.adaptiveRating.TestFixtures.EPS;
import static com.adaptiveRating.TestFixtures.history;
import static com.adaptiveRating.TestFixtures.rating;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PercentileCalculatorTest {

    private final PercentileCalculator calculator = new PercentileCalculator();

    @Test
    @DisplayName("rating lower than all previous history has percentile zero")
    void ratingLowerThanAllHistoryHasPercentileZero() {
        List<Rating> past = history("u1", 8, 9, 10);
        Rating current = rating("u1", 1.0);

        double percentile = calculator.calculatePercentile(current, past);

        assertThat(percentile).isCloseTo(0.0, within(EPS));
    }

    @Test
    @DisplayName("rating higher than all previous history has percentile hundred")
    void ratingHigherThanAllHistoryHasPercentileHundred() {
        List<Rating> past = history("u1", 1, 2, 3);
        Rating current = rating("u1", 10.0);

        double percentile = calculator.calculatePercentile(current, past);

        assertThat(percentile).isCloseTo(100.0, within(EPS));
    }

    @Test
    @DisplayName("equal ratings contribute half weight in percentile formula")
    void equalRatingsContributeHalfWeight() {
        List<Rating> past = history("u1", 5, 5, 5, 5);
        Rating current = rating("u1", 5.0);

        double percentile = calculator.calculatePercentile(current, past);

        double expected = (0.0 + 0.5 * 4.0) / 4.0 * 100.0;
        assertThat(percentile).isCloseTo(expected, within(EPS));
        assertThat(percentile).isCloseTo(50.0, within(EPS));
    }

    @Test
    @DisplayName("mixed history uses less than and equals counts correctly")
    void mixedHistoryUsesLessThanAndEqualsCounts() {
        List<Rating> past = history("u1", 1, 2, 5, 5, 9);
        Rating current = rating("u1", 5.0);

        double percentile = calculator.calculatePercentile(current, past);

        double expected = (2.0 + 0.5 * 2.0) / 5.0 * 100.0;
        assertThat(percentile).isCloseTo(expected, within(EPS));
        assertThat(percentile).isCloseTo(60.0, within(EPS));
    }

    @Test
    @DisplayName("current rating must not be included in own history and changes percentile")
    void currentRatingMustNotBeIncludedInOwnHistory() {
        List<Rating> pastOnly = history("u1", 1, 1, 1);
        Rating current = rating("u1", 10.0);

        double withoutCurrent = calculator.calculatePercentile(current, pastOnly);

        List<Rating> pastPlusCurrent = new ArrayList<>(pastOnly);
        pastPlusCurrent.add(current);
        double withCurrent = calculator.calculatePercentile(current, pastPlusCurrent);

        assertThat(withoutCurrent).isCloseTo(100.0, within(EPS));
        assertThat(withCurrent).isCloseTo((3.0 + 0.5 * 1.0) / 4.0 * 100.0, within(EPS));
        assertThat(withCurrent).isLessThan(withoutCurrent);
    }
}