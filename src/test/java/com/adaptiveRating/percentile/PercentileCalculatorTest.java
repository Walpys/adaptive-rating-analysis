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
    @DisplayName("оцінка нижча за всі попередні має процентиль 0")
    void ratingLowerThanAllHistoryHasPercentileZero() {
        List<Rating> past = history("u1", 8, 9, 10);
        Rating current = rating("u1", 1.0);

        double percentile = calculator.calculatePercentile(current, past);

        assertThat(percentile).isCloseTo(0.0, within(EPS));
    }

    @Test
    @DisplayName("оцінка вища за всі попередні має процентиль 100")
    void ratingHigherThanAllHistoryHasPercentileHundred() {
        List<Rating> past = history("u1", 1, 2, 3);
        Rating current = rating("u1", 10.0);

        double percentile = calculator.calculatePercentile(current, past);

        assertThat(percentile).isCloseTo(100.0, within(EPS));
    }

    @Test
    @DisplayName("рівні оцінки враховуються як 0.5 у формулі процентиля")
    void equalRatingsContributeHalfWeight() {
        List<Rating> past = history("u1", 5, 5, 5, 5);
        Rating current = rating("u1", 5.0);

        double percentile = calculator.calculatePercentile(current, past);

        double expected = (0 + 0.5 * 4) / 4.0 * 100.0;
        assertThat(percentile).isCloseTo(expected, within(EPS));
        assertThat(percentile).isCloseTo(50.0, within(EPS));
    }

    @Test
    @DisplayName("змішана історія: частина нижче, частина рівна, частина вище")
    void mixedHistoryUsesLessThanAndEqualsCounts() {
        List<Rating> past = history("u1", 1, 2, 5, 5, 9);
        Rating current = rating("u1", 5.0);

        double percentile = calculator.calculatePercentile(current, past);

        double expected = (2 + 0.5 * 2) / 5.0 * 100.0;
        assertThat(percentile).isCloseTo(expected, within(EPS));
        assertThat(percentile).isCloseTo(60.0, within(EPS));
    }

    @Test
    @DisplayName("поточна оцінка не повинна входити у власну історію: додавання її змінює процентиль")
    void currentRatingMustNotBeIncludedInOwnHistory() {
        List<Rating> pastOnly = history("u1", 1, 1, 1);
        Rating current = rating("u1", 10.0);

        double withoutCurrent = calculator.calculatePercentile(current, pastOnly);

        List<Rating> pastPlusCurrent = new ArrayList<>(pastOnly);
        pastPlusCurrent.add(current);
        double withCurrent = calculator.calculatePercentile(current, pastPlusCurrent);

        assertThat(withoutCurrent).isCloseTo(100.0, within(EPS));
        assertThat(withCurrent).isCloseTo((3 + 0.5 * 1) / 4.0 * 100.0, within(EPS));
        assertThat(withCurrent).isLessThan(withoutCurrent);
    }
}
