package com.adaptiveRating.anomaly;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.adaptiveRating.TestFixtures.EPS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AnomalyScoreCalculatorTest {

    @Test
    @DisplayName("об'єднує Personal, Cluster, Temporal і Object Consensus з вагами з конфігу")
    void combinesAllFourSignalsUsingConfigWeights() {
        double wPersonal = 0.4;
        double wCluster = 0.3;
        double wTime = 0.2;
        double wObject = 0.1;
        AnomalyScoreCalculator calculator = new AnomalyScoreCalculator(wPersonal, wCluster, wTime, wObject);

        Optional<Double> score = calculator.calculateScore(
                Optional.of(0.5),
                Optional.of(1.0),
                Optional.of(0.0),
                Optional.of(0.9)
        );

        double expected = (0.4 * 0.5 + 0.3 * 1.0 + 0.2 * 0.0 + 0.1 * 0.9) / 1.0;
        assertThat(score).isPresent();
        assertThat(score.get()).isCloseTo(expected, within(EPS));
        assertThat(score.get()).isCloseTo(0.59, within(EPS));
    }

    @Test
    @DisplayName("відсутній cluster-сигнал виключає його вагу і нормалізує решту")
    void missingClusterSignalRenormalizesActiveWeights() {
        AnomalyScoreCalculator calculator = new AnomalyScoreCalculator(0.4, 0.3, 0.2, 0.1);

        Optional<Double> score = calculator.calculateScore(
                Optional.of(1.0),
                Optional.empty(),
                Optional.of(0.0),
                Optional.of(0.5)
        );

        double expected = (0.4 * 1.0 + 0.2 * 0.0 + 0.1 * 0.5) / (0.4 + 0.2 + 0.1);
        assertThat(score).isPresent();
        assertThat(score.get()).isCloseTo(expected, within(EPS));
    }

    @Test
    @DisplayName("усі нульові сигнали дають AnomalyScore 0.0")
    void allZeroSignalsYieldZeroScore() {
        AnomalyScoreCalculator calculator = new AnomalyScoreCalculator(0.4, 0.3, 0.2, 0.1);

        Optional<Double> score = calculator.calculateScore(
                Optional.of(0.0),
                Optional.of(0.0),
                Optional.of(0.0),
                Optional.of(0.0)
        );

        assertThat(score).isPresent();
        assertThat(score.get()).isCloseTo(0.0, within(EPS));
    }

    @Test
    @DisplayName("усі одиничні сигнали дають AnomalyScore 1.0")
    void allOneSignalsYieldOneScore() {
        AnomalyScoreCalculator calculator = new AnomalyScoreCalculator(0.4, 0.3, 0.2, 0.1);

        Optional<Double> score = calculator.calculateScore(
                Optional.of(1.0),
                Optional.of(1.0),
                Optional.of(1.0),
                Optional.of(1.0)
        );

        assertThat(score).isPresent();
        assertThat(score.get()).isCloseTo(1.0, within(EPS));
    }

    @Test
    @DisplayName("результат завжди затискається в [0.0, 1.0]")
    void scoreIsClampedToUnitInterval() {
        AnomalyScoreCalculator calculator = new AnomalyScoreCalculator(1.0, 0.0, 0.0, 0.0);

        Optional<Double> high = calculator.calculateScore(
                Optional.of(2.5), Optional.empty(), Optional.empty(), Optional.empty()
        );
        Optional<Double> low = calculator.calculateScore(
                Optional.of(-1.0), Optional.empty(), Optional.empty(), Optional.empty()
        );

        assertThat(high).contains(1.0);
        assertThat(low).contains(0.0);
    }

    @Test
    @DisplayName("якщо немає жодного активного сигналу, результат порожній")
    void noActiveSignalsReturnsEmpty() {
        AnomalyScoreCalculator calculator = new AnomalyScoreCalculator(0.4, 0.3, 0.2, 0.1);

        Optional<Double> score = calculator.calculateScore(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        assertThat(score).isEmpty();
    }

    @Test
    @DisplayName("нульова вага ігнорує навіть присутній сигнал")
    void zeroWeightIgnoresPresentSignal() {
        AnomalyScoreCalculator calculator = new AnomalyScoreCalculator(1.0, 0.0, 0.0, 0.0);

        Optional<Double> score = calculator.calculateScore(
                Optional.of(0.2),
                Optional.of(1.0),
                Optional.of(1.0),
                Optional.of(1.0)
        );

        assertThat(score).contains(0.2);
    }

    @Test
    @DisplayName("ваги, що не сумуються до 1, все одно нормалізуються через activeWeightSum")
    void weightsThatDoNotSumToOneAreRenormalized() {
        AnomalyScoreCalculator calculator = new AnomalyScoreCalculator(0.5, 0.3, 0.2, 0.2);

        Optional<Double> score = calculator.calculateScore(
                Optional.of(1.0),
                Optional.of(0.0),
                Optional.of(0.0),
                Optional.of(0.0)
        );

        double expected = 0.5 / (0.5 + 0.3 + 0.2 + 0.2);
        assertThat(score.get()).isCloseTo(expected, within(EPS));
    }
}
