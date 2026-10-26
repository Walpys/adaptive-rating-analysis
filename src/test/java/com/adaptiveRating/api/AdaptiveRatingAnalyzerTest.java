package com.adaptiveRating.api;

import com.adaptiveRating.config.AnalyzerConfig;
import com.adaptiveRating.datasource.InMemoryRatingDataSource;
import com.adaptiveRating.exception.AppIllegalArgumentException;
import com.adaptiveRating.exception.cluster.ClusterNotFoundException;
import com.adaptiveRating.exception.rating.RatingValidationException;
import com.adaptiveRating.model.AnalysisResult;
import com.adaptiveRating.model.ClusterModel;
import com.adaptiveRating.model.Rating;
import com.adaptiveRating.model.enums.AnalysisStatus;
import com.adaptiveRating.model.enums.ResultClassification;
import com.adaptiveRating.profile.UserProfileBuilder;
import com.adaptiveRating.validation.ValidationUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.adaptiveRating.TestFixtures.EPS;
import static com.adaptiveRating.TestFixtures.config;
import static com.adaptiveRating.TestFixtures.history;
import static com.adaptiveRating.TestFixtures.rating;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class AdaptiveRatingAnalyzerTest {

    @Nested
    @DisplayName("недостатньо даних")
    class InsufficientData {

        @Test
        @DisplayName("історія коротша за minRatings повертає INSUFFICIENT_DATA")
        void shortHistoryReturnsInsufficientData() {
            AnalyzerConfig cfg = config(5);
            AdaptiveRatingAnalyzer analyzer = new AdaptiveRatingAnalyzer(cfg);
            Rating current = rating("u1", 8.0);
            List<Rating> shortHistory = history("u1", 5, 6, 7);

            AnalysisResult result = analyzer.analyze(current, shortHistory, null, -1);

            assertThat(result.status()).isEqualTo(AnalysisStatus.INSUFFICIENT_DATA);
            assertThat(result.classification()).isNull();
            assertThat(result.getPersonalPercentile()).isEmpty();
            assertThat(result.getPersonalAnomaly()).isEmpty();
            assertThat(result.getClusterAnomaly()).isEmpty();
            assertThat(result.getTemporalAnomaly()).isEmpty();
            assertThat(result.getAnomalyScore()).isEmpty();
            assertThat(result.getClusterId()).isEmpty();
            assertThat(result.rawRating()).isEqualTo(current);
        }

        @Test
        @DisplayName("defaultConfig вимагає 20 оцінок: 19 — INSUFFICIENT_DATA, 20 — уже не цей статус")
        void defaultMinRatingsBoundary() {
            AdaptiveRatingAnalyzer analyzer = new AdaptiveRatingAnalyzer(AnalyzerConfig.defaultConfig());
            Rating current = rating("u1", 5.0);

            List<Rating> nineteen = nRatings("u1", 19, 5.0);
            List<Rating> twenty = nRatings("u1", 20, 5.0);

            assertThat(analyzer.analyze(current, nineteen, null, -1).status())
                    .isEqualTo(AnalysisStatus.INSUFFICIENT_DATA);
            assertThat(analyzer.analyze(current, twenty, null, -1).status())
                    .isEqualTo(AnalysisStatus.PARTIAL);
        }
    }

    @Nested
    @DisplayName("fail-fast валідація через ValidationUtils")
    class FailFastValidation {

        private final AdaptiveRatingAnalyzer analyzer = new AdaptiveRatingAnalyzer(config(3));

        @Test
        @DisplayName("null rating у analyze(dataSource) кидає AppIllegalArgumentException")
        void nullRatingWithDataSource() {
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(List.of());

            assertThatThrownBy(() -> analyzer.analyze(null, dataSource, null, -1))
                    .isInstanceOf(AppIllegalArgumentException.class)
                    .hasMessage("rating must not be null");
        }

        @Test
        @DisplayName("null dataSource кидає AppIllegalArgumentException")
        void nullDataSource() {
            assertThatThrownBy(() -> analyzer.analyze(rating("u1", 5.0), (InMemoryRatingDataSource) null, null, -1))
                    .isInstanceOf(AppIllegalArgumentException.class)
                    .hasMessage("dataSource must not be null");
        }

        @Test
        @DisplayName("null rating у analyze(history) кидає AppIllegalArgumentException")
        void nullRatingWithHistory() {
            assertThatThrownBy(() -> analyzer.analyze(null, List.of(), null, -1))
                    .isInstanceOf(AppIllegalArgumentException.class)
                    .hasMessage("rating must not be null");
        }

        @Test
        @DisplayName("null userHistory кидає AppIllegalArgumentException")
        void nullHistory() {
            assertThatThrownBy(() -> analyzer.analyze(rating("u1", 5.0), (List<Rating>) null, null, -1))
                    .isInstanceOf(AppIllegalArgumentException.class)
                    .hasMessage("userHistory must not be null");
        }

        @Test
        @DisplayName("історія іншого користувача відхиляється RatingValidator")
        void historyMustBelongToUser() {
            List<Rating> foreignHistory = history("other-user", 5, 5, 5);

            assertThatThrownBy(() -> analyzer.analyze(rating("u1", 5.0), foreignHistory, null, -1))
                    .isInstanceOf(RatingValidationException.class)
                    .hasMessageContaining("mismatched userId");
        }

        @Test
        @DisplayName("ValidationUtils.requireNonNull — пряма перевірка контракту")
        void validationUtilsRejectsNull() {
            assertThatThrownBy(() -> ValidationUtils.requireNonNull(null, "rating must not be null"))
                    .isInstanceOf(AppIllegalArgumentException.class)
                    .hasMessage("rating must not be null");
        }
    }

    @Nested
    @DisplayName("повний аналіз і класифікація")
    class FullAnalysis {

        @Test
        @DisplayName("стабільна історія без кластера дає PARTIAL, низький score і NORMAL")
        void stableHistoryWithoutClusterIsPartialNormal() {
            AdaptiveRatingAnalyzer analyzer = new AdaptiveRatingAnalyzer(config(3));
            List<Rating> past = history("u1", 5, 5, 5, 5, 5);
            Rating current = rating("u1", 5.0);

            AnalysisResult result = analyzer.analyze(current, past, null, -1);

            assertThat(result.status()).isEqualTo(AnalysisStatus.PARTIAL);
            assertThat(result.classification()).isEqualTo(ResultClassification.NORMAL);
            assertThat(result.getClusterAnomaly()).isEmpty();
            assertThat(result.getAnomalyScore()).isPresent();
            assertThat(result.anomalyScore()).isCloseTo(0.0, within(0.15));
            assertThat(result.personalAnomaly()).isCloseTo(0.0, within(EPS));
            assertThat(result.temporalAnomaly()).isZero();
        }

        @Test
        @DisplayName("валідна кластерна модель дає COMPLETE і рахує cluster anomaly")
        void validClusterProducesCompleteStatus() {
            AdaptiveRatingAnalyzer analyzer = new AdaptiveRatingAnalyzer(config(3));
            List<Rating> past = history("u1", 5, 5, 5, 5);
            Rating current = rating("u1", 5.0);
            ClusterModel model = new ClusterModel(
                    1,
                    List.of(new UserProfileBuilder().build("u1", past).userVector()),
                    1L
            );

            AnalysisResult result = analyzer.analyze(current, past, model, 0);

            assertThat(result.status()).isEqualTo(AnalysisStatus.COMPLETE);
            assertThat(result.getClusterId()).contains("Cluster-0");
            assertThat(result.getClusterAnomaly()).isPresent();
            assertThat(result.clusterAnomaly()).isCloseTo(0.0, within(1e-6));
        }

        @Test
        @DisplayName("індекс кластера поза межами моделі — ClusterNotFoundException")
        void invalidClusterIndexFailsFast() {
            AdaptiveRatingAnalyzer analyzer = new AdaptiveRatingAnalyzer(config(3));
            List<Rating> past = history("u1", 5, 5, 5);
            ClusterModel model = new ClusterModel(
                    1,
                    List.of(new UserProfileBuilder().build("u1", past).userVector()),
                    1L
            );

            assertThatThrownBy(() -> analyzer.analyze(rating("u1", 5.0), past, model, 4))
                    .isInstanceOf(ClusterNotFoundException.class)
                    .hasMessageContaining("Invalid cluster index");
        }

        @Test
        @DisplayName("рідкісна оцінка і розбіжність із консенсусом об'єкта піднімають AnomalyScore до ANOMALOUS")
        void rareRatingAndObjectDisagreementClassifiedAnomalous() {
            AnalyzerConfig strict = new AnalyzerConfig(
                    4, 3, 100, 42L,
                    0.4, 0.0, 0.0, 0.3,
                    Duration.ofHours(1), 50, 0.6
            );
            AdaptiveRatingAnalyzer analyzer = new AdaptiveRatingAnalyzer(strict);

            List<Rating> all = new ArrayList<>();
            all.addAll(history("u1", 5, 5, 5, 5));
            all.add(rating("u2", "obj-1", 1.0));
            all.add(rating("u3", "obj-1", 1.0));
            all.add(rating("u4", "obj-1", 1.0));
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(all);

            Rating current = rating("u1", "obj-1", 10.0);
            AnalysisResult result = analyzer.analyze(current, dataSource, null, -1);

            assertThat(result.status()).isEqualTo(AnalysisStatus.PARTIAL);
            assertThat(result.personalAnomaly()).isEqualTo(1.0);
            assertThat(result.getAnomalyScore()).isPresent();
            assertThat(result.anomalyScore()).isGreaterThanOrEqualTo(0.3);
            assertThat(result.classification()).isEqualTo(ResultClassification.ANOMALOUS);
        }

        @Test
        @DisplayName("analyze(dataSource) читає історію користувача з джерела")
        void dataSourceOverloadUsesUserHistory() {
            AdaptiveRatingAnalyzer analyzer = new AdaptiveRatingAnalyzer(config(3));
            List<Rating> stored = history("u1", 6, 6, 6, 6);
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(stored);

            AnalysisResult result = analyzer.analyze(rating("u1", 6.0), dataSource, null, -1);

            assertThat(result.status()).isEqualTo(AnalysisStatus.PARTIAL);
            assertThat(result.getPersonalPercentile()).isPresent();
            assertThat(result.personalPercentile()).isCloseTo(50.0, within(EPS));
        }
    }

    private static List<Rating> nRatings(String userId, int count, double value) {
        List<Rating> ratings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ratings.add(rating(userId, value));
        }
        return ratings;
    }
}
