package com.adaptiveRating.anomaly;

import com.adaptiveRating.exception.anomaly.AnomalyCalculationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.adaptiveRating.TestFixtures.EPS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class AnomalyScoreCalculatorTest {

    @Test
    @DisplayName("combines Personal, Cluster, Temporal, and Object Consensus signals using config weights")
    void combinesAllFourSignalsUsingConfigWeights() {
        double wPersonal = 0.4;
        double wCluster = 0.3;
        double wTime = 0.2;
        double wObject = 0.1;
        AnomalyScoreCalculator calculator = new AnomalyScoreCalculator(wPersonal, wCluster, wTime, wObject);

        double score = calculator.calculateScore(0.5, 1.0, 0.0, 0.9);

        double expected = (0.4 * 0.5 + 0.3 * 1.0 + 0.2 * 0.0 + 0.1 * 0.9) / 1.0;
        assertThat(score).isCloseTo(expected, within(EPS));
        assertThat(score).isCloseTo(0.59, within(EPS));
    }

    @Test
    @DisplayName("null signal excludes its weight and renormalizes remaining active weights")
    void missingClusterSignalRenormalizesActiveWeights() {
        AnomalyScoreCalculator calculator = new AnomalyScoreCalculator(0.4, 0.3, 0.2, 0.1);

        double score = calculator.calculateScore(1.0, null, 0.0, 0.5);

        double expected = (0.4 * 1.0 + 0.2 * 0.0 + 0.1 * 0.5) / (0.4 + 0.2 + 0.1);
        assertThat(score).isCloseTo(expected, within(EPS));
    }

    @Test
    @DisplayName("all zero signals yield anomaly score of zero")
    void allZeroSignalsYieldZeroScore() {
        AnomalyScoreCalculator calculator = new AnomalyScoreCalculator(0.4, 0.3, 0.2, 0.1);

        double score = calculator.calculateScore(0.0, 0.0, 0.0, 0.0);

        assertThat(score).isCloseTo(0.0, within(EPS));
    }

    @Test
    @DisplayName("all unit signals yield anomaly score of one")
    void allOneSignalsYieldOneScore() {
        AnomalyScoreCalculator calculator = new AnomalyScoreCalculator(0.4, 0.3, 0.2, 0.1);

        double score = calculator.calculateScore(1.0, 1.0, 1.0, 1.0);

        assertThat(score).isCloseTo(1.0, within(EPS));
    }

    @Test
    @DisplayName("result is always clamped to unit interval [0.0, 1.0]")
    void scoreIsClampedToUnitInterval() {
        AnomalyScoreCalculator calculator = new AnomalyScoreCalculator(1.0, 0.0, 0.0, 0.0);

        double high = calculator.calculateScore(2.5, null, null, null);
        double low = calculator.calculateScore(-1.0, null, null, null);

        assertThat(high).isEqualTo(1.0);
        assertThat(low).isEqualTo(0.0);
    }

    @Test
    @DisplayName("throws exception when no active anomaly signals are provided")
    void noActiveSignalsThrowsException() {
        AnomalyScoreCalculator calculator = new AnomalyScoreCalculator(0.4, 0.3, 0.2, 0.1);

        assertThatThrownBy(() -> calculator.calculateScore(null, null, null, null))
                .isInstanceOf(AnomalyCalculationException.class)
                .hasMessageContaining("active weight sum is zero");
    }

    @Test
    @DisplayName("zero weight ignores even present signal")
    void zeroWeightIgnoresPresentSignal() {
        AnomalyScoreCalculator calculator = new AnomalyScoreCalculator(1.0, 0.0, 0.0, 0.0);

        double score = calculator.calculateScore(0.2, 1.0, 1.0, 1.0);

        assertThat(score).isCloseTo(0.2, within(EPS));
    }

    @Test
    @DisplayName("weights that do not sum to one are still normalized via activeWeightSum")
    void weightsThatDoNotSumToOneAreRenormalized() {
        AnomalyScoreCalculator calculator = new AnomalyScoreCalculator(0.5, 0.3, 0.2, 0.2);

        double score = calculator.calculateScore(1.0, 0.0, 0.0, 0.0);

        double expected = 0.5 / (0.5 + 0.3 + 0.2 + 0.2);
        assertThat(score).isCloseTo(expected, within(EPS));
    }
}