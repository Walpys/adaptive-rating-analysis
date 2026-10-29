package com.adaptiveRating.api;

import com.adaptiveRating.anomaly.*;
import com.adaptiveRating.config.AnalyzerConfig;
import com.adaptiveRating.datasource.InMemoryRatingDataSource;
import com.adaptiveRating.datasource.RatingDataSource;
import com.adaptiveRating.exception.AppIllegalArgumentException;
import com.adaptiveRating.exception.rating.RatingValidationException;
import com.adaptiveRating.model.*;
import com.adaptiveRating.model.enums.AnalysisStatus;
import com.adaptiveRating.model.enums.ResultClassification;
import com.adaptiveRating.percentile.PercentileCalculator;
import com.adaptiveRating.profile.UserProfileBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.adaptiveRating.TestFixtures.EPS;
import static com.adaptiveRating.TestFixtures.config;
import static com.adaptiveRating.TestFixtures.history;
import static com.adaptiveRating.TestFixtures.rating;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class AdaptiveRatingAnalyzerApiTest {

    private final UserProfileBuilder profileBuilder = new UserProfileBuilder();

    private AdaptiveRatingAnalyzerApi createAnalyzer(AnalyzerConfig cfg, RatingDataSource ds) {
        return new AdaptiveRatingAnalyzerApi(
                cfg,
                ds,
                new PersonalAnomalyDetector(),
                new ClusterAnomalyDetector(),
                new ObjectConsensusAnomalyDetector(),
                new TemporalAnomalyDetector(cfg.getTemporalWindow(), cfg.getMaxTemporalCountInWindow()),
                new AnomalyScoreCalculator(
                        cfg.getWeightPersonal(),
                        cfg.getWeightCluster(),
                        cfg.getWeightTime(),
                        cfg.getWeightObject()
                ),
                new PercentileCalculator(),
                new DispersionAnomalyDetector()
        );
    }

    @Nested
    @DisplayName("fail-fast validation")
    class FailFastValidation {

        @Test
        @DisplayName("null rating in context throws exception")
        void nullRatingInContext() {
            AnalyzerConfig cfg = config(3);
            InMemoryRatingDataSource ds = new InMemoryRatingDataSource(List.of());
            AdaptiveRatingAnalyzerApi analyzer = createAnalyzer(cfg, ds);

            List<Rating> history = history("u1", 5, 5, 5);
            UserProfile profile = profileBuilder.build("u1", history);
            ClusterModel clusterModel = new ClusterModel(1, List.of(profile.getUserVector()), 1L);

            AnalysisContext context = new AnalysisContext(null, history, profile, clusterModel, List.of());

            assertThatThrownBy(() -> analyzer.analyze(context))
                    .isInstanceOf(AppIllegalArgumentException.class);
        }

        @Test
        @DisplayName("history shorter than minRatings throws RatingValidationException")
        void shortHistoryThrowsException() {
            AnalyzerConfig cfg = config(5);
            InMemoryRatingDataSource ds = new InMemoryRatingDataSource(List.of());
            AdaptiveRatingAnalyzerApi analyzer = createAnalyzer(cfg, ds);

            Rating current = rating("u1", 8.0);
            List<Rating> shortHistory = history("u1", 5, 6, 7);
            UserProfile profile = profileBuilder.build("u1", shortHistory);
            ClusterModel clusterModel = new ClusterModel(1, List.of(profile.getUserVector()), 1L);

            AnalysisContext context = new AnalysisContext(current, shortHistory, profile, clusterModel, List.of());

            assertThatThrownBy(() -> analyzer.analyze(context))
                    .isInstanceOf(RatingValidationException.class)
                    .hasMessageContaining("Insufficient ratings");
        }
    }

    @Nested
    @DisplayName("full analysis and classification")
    class FullAnalysis {

        @Test
        @DisplayName("valid context produces complete analysis result with score and classification")
        void validContextProducesCompleteResult() {
            AnalyzerConfig cfg = config(3);
            InMemoryRatingDataSource ds = new InMemoryRatingDataSource(List.of());
            AdaptiveRatingAnalyzerApi analyzer = createAnalyzer(cfg, ds);

            List<Rating> past = history("u1", 5, 5, 5, 5);
            Rating current = rating("u1", "obj-1", 5.0);
            UserProfile profile = profileBuilder.build("u1", past);
            ClusterModel clusterModel = new ClusterModel(
                    1,
                    List.of(profile.getUserVector()),
                    1L
            );
            profile.setClusterIndex(0);

            AnalysisContext context = new AnalysisContext(current, past, profile, clusterModel, List.of(current));

            AnalysisResult result = analyzer.analyze(context);

            assertThat(result.status()).isEqualTo(AnalysisStatus.COMPLETE);
            assertThat(result.classification()).isEqualTo(ResultClassification.NORMAL);
            assertThat(result.getClusterId()).contains("0");
            assertThat(result.clusterAnomaly()).isCloseTo(0.0, within(1e-6));
            assertThat(result.anomalyScore()).isCloseTo(0.0, within(0.15));
        }

        @Test
        @DisplayName("analyze via userId fetches from datasource and performs analysis")
        void analyzeViaUserIdFetchesFromDataSource() {
            AnalyzerConfig cfg = config(3);
            List<Rating> past = history("u1", 6, 6, 6, 6);
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(past);
            AdaptiveRatingAnalyzerApi analyzer = createAnalyzer(cfg, dataSource);

            UserProfile profile = profileBuilder.build("u1", past);
            profile.setClusterIndex(0);
            dataSource.saveProfile(profile);

            ClusterModel clusterModel = new ClusterModel(1, List.of(profile.getUserVector()), 1L);

            Rating current = rating("u1", "obj-1", 6.0);
            AnalysisResult result = analyzer.analyze("u1", current, clusterModel);

            assertThat(result.status()).isEqualTo(AnalysisStatus.COMPLETE);
            assertThat(result.personalPercentile()).isCloseTo(50.0, within(EPS));
            assertThat(result.classification()).isEqualTo(ResultClassification.NORMAL);
        }

        @Test
        @DisplayName("valid user profile produces complete profile analysis result")
        void validProfileProducesCompleteResult() {
            AnalyzerConfig cfg = config(3);
            List<Rating> past = history("u1", 7, 8, 9, 8, 7);
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(past);
            AdaptiveRatingAnalyzerApi analyzer = createAnalyzer(cfg, dataSource);

            UserProfile profile = profileBuilder.build("u1", past);
            profile.setClusterIndex(0);
            dataSource.saveProfile(profile);

            ClusterModel clusterModel = new ClusterModel(1, List.of(profile.getUserVector()), 1L);

            ProfileAnalysisResult result = analyzer.analyzeProfile("u1", clusterModel);

            assertThat(result).isNotNull();
            assertThat(result.userId()).isEqualTo("u1");
            assertThat(result.totalRatings()).isEqualTo(5);
            assertThat(result.status()).isEqualTo(AnalysisStatus.COMPLETE);
            assertThat(result.classification()).isEqualTo(ResultClassification.NORMAL);
            assertThat(result.clusterId()).contains("0");
            assertThat(result.clusterAnomaly()).isCloseTo(0.0, within(1e-6));
            assertThat(result.dispersionAnomaly()).isZero();
            assertThat(result.overallAnomalyScore()).isCloseTo(0.0, within(0.15));
        }

        @Test
        @DisplayName("missing user profile in datasource throws exception during profile analysis")
        void missingProfileThrowsException() {
            AnalyzerConfig cfg = config(3);
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(List.of());
            AdaptiveRatingAnalyzerApi analyzer = createAnalyzer(cfg, dataSource);

            ClusterModel clusterModel = new ClusterModel(1, List.of(profileBuilder.build("u1", history("u1", 5, 5, 5)).getUserVector()), 1L);

            assertThatThrownBy(() -> analyzer.analyzeProfile("non-existent-user", clusterModel))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("UserProfile must exist in data source");
        }
    }
}