package io.github.walpys.adaptiverating.api;

import io.github.walpys.adaptiverating.anomaly.*;
import io.github.walpys.adaptiverating.config.AnalyzerConfig;
import io.github.walpys.adaptiverating.config.WindowRule;
import io.github.walpys.adaptiverating.datasource.InMemoryRatingDataSource;
import io.github.walpys.adaptiverating.datasource.RatingDataSource;
import io.github.walpys.adaptiverating.exception.AppIllegalArgumentException;
import io.github.walpys.adaptiverating.exception.rating.RatingValidationException;
import io.github.walpys.adaptiverating.model.*;
import io.github.walpys.adaptiverating.model.enums.AnalysisStatus;
import io.github.walpys.adaptiverating.model.enums.ResultClassification;
import io.github.walpys.adaptiverating.percentile.PercentileCalculator;
import io.github.walpys.adaptiverating.profile.UserProfileBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.github.walpys.adaptiverating.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class AdaptiveRatingAnalyzerTest {

    private final UserProfileBuilder profileBuilder = new UserProfileBuilder();

    private AdaptiveRatingAnalyzer createAnalyzer(AnalyzerConfig cfg, RatingDataSource ds) {
        var firstRule = (cfg.getTemporalRules() != null && !cfg.getTemporalRules().isEmpty())
                ? cfg.getTemporalRules().get(0)
                : new WindowRule(Duration.ofHours(1), 5, 20);

        return AdaptiveRatingAnalyzer.builder()
                .config(cfg)
                .dataSource(ds)
                .personalDetector(new PersonalAnomalyDetector())
                .clusterDetector(new ClusterAnomalyDetector())
                .temporalAnomalyDetector(new TemporalAnomalyDetector(List.of(firstRule)))
                .anomalyScoreCalculator(new AnomalyScoreCalculator(
                        cfg.getWeightPersonal(),
                        cfg.getWeightCluster(),
                        cfg.getWeightTime(),
                        cfg.getWeightObject()
                ))
                .percentileCalculator(new PercentileCalculator())
                .dispersionAnomalyDetector(new DispersionAnomalyDetector())
                .clusterCount(1)
                .build();
    }

    @Nested
    @DisplayName("fail-fast validation")
    class FailFastValidation {

        @Test
        @DisplayName("null rating in context throws exception")
        void nullRatingInContext() {
            AnalyzerConfig cfg = config(3);
            InMemoryRatingDataSource ds = new InMemoryRatingDataSource(List.of());
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(cfg, ds);

            List<Rating> history = history("u1", 5, 5, 5);
            UserProfile profile = profileBuilder.build("u1", history);
            ClusterModel clusterModel = new ClusterModel(1, List.of(profile.getUserVector()), List.of(0), 1L, List.of(0.0));

            AnalysisContext context = new AnalysisContext(null, history, profile, clusterModel);

            assertThatThrownBy(() -> analyzer.analyze(context))
                    .isInstanceOf(AppIllegalArgumentException.class);
        }

        @Test
        @DisplayName("history shorter than minRatings throws RatingValidationException")
        void shortHistoryThrowsException() {
            AnalyzerConfig cfg = config(5);
            InMemoryRatingDataSource ds = new InMemoryRatingDataSource(List.of());
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(cfg, ds);

            Rating current = rating("u1", 8.0);
            List<Rating> shortHistory = history("u1", 5, 6, 7);
            UserProfile profile = profileBuilder.build("u1", shortHistory);
            ClusterModel clusterModel = new ClusterModel(1, List.of(profile.getUserVector()), List.of(0), 1L, List.of(0.0));

            AnalysisContext context = new AnalysisContext(current, shortHistory, profile, clusterModel);

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
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(cfg, ds);

            List<Rating> past = history("u1", 5, 5, 5, 5);
            Rating current = rating("u1", "obj-1", 5.0);
            UserProfile profile = profileBuilder.build("u1", past);
            ClusterModel clusterModel = new ClusterModel(
                    1,
                    List.of(profile.getUserVector()),
                    List.of(0),
                    1L,
                    List.of(0.0)
            );

            UserProfile profiledWithCluster = profile.withClusterIndex(0);

            AnalysisContext context = new AnalysisContext(current, past, profiledWithCluster, clusterModel);

            AnalysisResult result = analyzer.analyze(context);

            assertThat(result.status()).isEqualTo(AnalysisStatus.COMPLETE);
            assertThat(result.classification()).isEqualTo(ResultClassification.NORMAL);
            assertThat(result.getClusterId()).contains("0");
            assertThat(result.anomalyScore()).isCloseTo(0.0, within(0.15));
        }

        @Test
        @DisplayName("analyze via userId fetches from datasource and performs analysis")
        void analyzeViaUserIdFetchesFromDataSource() {
            AnalyzerConfig cfg = config(3);
            List<Rating> past = history("u1", 6, 6, 6, 6);
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(past);
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(cfg, dataSource);

            UserProfile profile = profileBuilder.build("u1", past).withClusterIndex(0);
            dataSource.saveProfile(profile);

            ClusterModel clusterModel = new ClusterModel(1, List.of(profile.getUserVector()), List.of(0), 1L, List.of(0.0));

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
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(cfg, dataSource);

            UserProfile profile = profileBuilder.build("u1", past).withClusterIndex(0);
            dataSource.saveProfile(profile);

            ClusterModel clusterModel = new ClusterModel(1, List.of(profile.getUserVector()), List.of(0), 1L, List.of(0.0));

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
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(cfg, dataSource);

            ClusterModel clusterModel = new ClusterModel(1, List.of(profileBuilder.build("u1", history("u1", 5, 5, 5)).getUserVector()), List.of(0), 1L, List.of(0.0));

            assertThatThrownBy(() -> analyzer.analyzeProfile("non-existent-user", clusterModel))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("UserProfile must exist in data source");
        }
    }

    @Nested
    @DisplayName("cluster analysis & building")
    class ClusterAnalysis {

        @Test
        @DisplayName("buildClusters builds model with K from config")
        void buildClustersUsesConfigK() {
            List<UserVector> vectors = List.of(unitAt(1), unitAt(5), unitAt(10));
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(config(3), new InMemoryRatingDataSource(List.of()));

            ClusterModel model = analyzer.buildClusters(vectors);

            assertThat(model.getK()).isEqualTo(3);
            assertThat(model.getClusterCenters()).hasSize(3);
            assertThat(model.getTrainingTimestamp()).isPositive();
        }

        @Test
        @DisplayName("buildClusters with customK uses passed K")
        void buildClustersWithCustomK() {
            List<UserVector> vectors = List.of(unitAt(1), unitAt(2), unitAt(9), unitAt(10));
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(config(3), new InMemoryRatingDataSource(List.of()));

            ClusterModel model = analyzer.buildClusters(vectors, 1);

            assertThat(model.getK()).isEqualTo(1);
            assertThat(model.getClusterCenters()).hasSize(1);
        }

        @Test
        @DisplayName("empty vectors fail-fast via ValidationUtils")
        void emptyVectorsFailFast() {
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(config(3), new InMemoryRatingDataSource(List.of()));

            assertThatThrownBy(() -> analyzer.buildClusters(List.of()))
                    .isInstanceOf(AppIllegalArgumentException.class)
                    .hasMessageContaining("userVectors");
        }

        @Test
        @DisplayName("null vectors fail-fast")
        void nullVectorsFailFast() {
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(config(3), new InMemoryRatingDataSource(List.of()));

            assertThatThrownBy(() -> analyzer.buildClusters(null))
                    .isInstanceOf(AppIllegalArgumentException.class)
                    .hasMessageContaining("userVectors");
        }

        @Test
        @DisplayName("non-positive customK fail-fast requirePositive")
        void nonPositiveCustomKFailFast() {
            List<UserVector> vectors = List.of(unitAt(1), unitAt(10));
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(config(3), new InMemoryRatingDataSource(List.of()));

            assertThatThrownBy(() -> analyzer.buildClusters(vectors, 0))
                    .isInstanceOf(AppIllegalArgumentException.class)
                    .hasMessageContaining("K must be positive");
        }

        @Test
        @DisplayName("same seed in config yields reproducible clusters")
        void configSeedIsReproducible() {
            AnalyzerConfig cfg = config(3);
            List<UserVector> vectors = List.of(unitAt(1), unitAt(4), unitAt(8), unitAt(10));

            AdaptiveRatingAnalyzer analyzer1 = createAnalyzer(cfg, new InMemoryRatingDataSource(List.of()));
            AdaptiveRatingAnalyzer analyzer2 = createAnalyzer(cfg, new InMemoryRatingDataSource(List.of()));

            ClusterModel first = analyzer1.buildClusters(vectors, 2);
            ClusterModel second = analyzer2.buildClusters(vectors, 2);

            assertThat(first.getK()).isEqualTo(second.getK());
            for (int i = 0; i < first.getClusterCenters().size(); i++) {
                assertThat(first.getClusterCenters().get(i).components())
                        .containsExactly(second.getClusterCenters().get(i).components());
            }
        }
    }

    @Nested
    @DisplayName("user profile management")
    class UserProfileManagement {

        @Test
        @DisplayName("buildProfile by userId fetches ratings from datasource and builds profile")
        void buildProfileByUserIdSuccess() {
            List<Rating> ratings = history("u1", 5, 6, 7, 8);
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(ratings);
            AnalyzerConfig cfg = config(3);
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(cfg, dataSource);

            UserProfile profile = analyzer.buildProfile("u1");

            assertThat(profile).isNotNull();
            assertThat(profile.getUserId()).isEqualTo("u1");
            assertThat(profile.getTotalRatings()).isEqualTo(4);
        }

        @Test
        @DisplayName("buildProfile with null userId throws exception")
        void buildProfileNullUserIdThrowsException() {
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(List.of());
            AnalyzerConfig cfg = config(3);
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(cfg, dataSource);

            assertThatThrownBy(() -> analyzer.buildProfile(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("userId must not be null");
        }

        @Test
        @DisplayName("buildProfile with insufficient ratings throws RatingValidationException")
        void buildProfileInsufficientRatingsThrowsException() {
            List<Rating> ratings = history("u1", 5, 5);
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(ratings);
            AnalyzerConfig cfg = config(3);
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(cfg, dataSource);

            assertThatThrownBy(() -> analyzer.buildProfile("u1"))
                    .isInstanceOf(RatingValidationException.class);
        }

        @Test
        @DisplayName("buildProfile with explicit rating list builds profile correctly")
        void buildProfileWithRatingListSuccess() {
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(List.of());
            AnalyzerConfig cfg = config(3);
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(cfg, dataSource);

            List<Rating> ratings = history("u1", 8, 9, 10, 7);
            UserProfile profile = analyzer.buildProfile("u1", ratings);

            assertThat(profile).isNotNull();
            assertThat(profile.getTotalRatings()).isEqualTo(4);
            assertThat(profile.getMean()).isGreaterThan(8.0);
        }

        @Test
        @DisplayName("buildProfilesBatch processes multiple valid users successfully")
        void buildProfilesBatchSuccess() {
            List<Rating> allRatings = List.of(
                    rating("u1", "obj-1", 5.0),
                    rating("u1", "obj-2", 5.0),
                    rating("u1", "obj-3", 5.0),
                    rating("u2", "obj-1", 8.0),
                    rating("u2", "obj-2", 8.0),
                    rating("u2", "obj-3", 8.0)
            );
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(allRatings);
            AnalyzerConfig cfg = config(3);
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(cfg, dataSource);

            Map<String, UserProfile> profiles = analyzer.buildProfilesBatch(List.of("u1", "u2"));

            assertThat(profiles).hasSize(2);
            assertThat(profiles).containsKey("u1");
            assertThat(profiles).containsKey("u2");
        }

        @Test
        @DisplayName("buildProfilesBatch skips users with insufficient ratings")
        void buildProfilesBatchSkipsInsufficientUsers() {
            List<Rating> allRatings = List.of(
                    rating("u1", "obj-1", 5.0),
                    rating("u1", "obj-2", 5.0),
                    rating("u1", "obj-3", 5.0),
                    rating("u2", "obj-1", 8.0)
            );
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(allRatings);
            AnalyzerConfig cfg = config(3);
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(cfg, dataSource);

            Map<String, UserProfile> profiles = analyzer.buildProfilesBatch(List.of("u1", "u2"));

            assertThat(profiles).hasSize(1);
            assertThat(profiles).containsKey("u1");
            assertThat(profiles).doesNotContainKey("u2");
        }

        @Test
        @DisplayName("buildProfilesBatch with empty list throws exception")
        void buildProfilesBatchEmptyListThrowsException() {
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(List.of());
            AnalyzerConfig cfg = config(3);
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(cfg, dataSource);

            assertThatThrownBy(() -> analyzer.buildProfilesBatch(List.of()))
                    .isInstanceOf(AppIllegalArgumentException.class);
        }

        @Test
        @DisplayName("buildProfileWithCluster assigns closest cluster index")
        void buildProfileWithClusterSuccess() {
            List<Rating> ratings = history("u1", 5, 5, 5, 5);
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(ratings);
            AnalyzerConfig cfg = config(3);
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(cfg, dataSource);

            UserVector center = unitAt(5);
            ClusterModel clusterModel = new ClusterModel(1, List.of(center), List.of(0), 1L, List.of(5.0));

            UserProfile profile = analyzer.buildProfileWithCluster("u1", clusterModel);

            assertThat(profile.getClusterIndex()).isNotNull();
            assertThat(profile.getClusterIndex()).isEqualTo(0);
        }

        @Test
        @DisplayName("updateClusterAssignment updates profile cluster index")
        void updateClusterAssignmentSuccess() {
            List<Rating> ratings = history("u1", 5, 5, 5, 5);
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(ratings);
            AnalyzerConfig cfg = config(3);
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(cfg, dataSource);

            UserProfile profile = profileBuilder.build("u1", ratings);
            UserVector center = unitAt(5);
            ClusterModel clusterModel = new ClusterModel(1, List.of(center), List.of(0), 1L, List.of(5.0));

            UserProfile updated = analyzer.updateClusterAssignment(profile, clusterModel);

            assertThat(updated.getClusterIndex()).isEqualTo(0);
        }

        @Test
        @DisplayName("updateClustersBatch updates multiple profiles cluster indices")
        void updateClustersBatchSuccess() {
            List<Rating> ratings1 = history("u1", 5, 5, 5, 5);
            List<Rating> ratings2 = history("u2", 1, 1, 1, 1);
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(List.of());
            AnalyzerConfig cfg = config(3);
            AdaptiveRatingAnalyzer analyzer = createAnalyzer(cfg, dataSource);

            UserProfile p1 = profileBuilder.build("u1", ratings1);
            UserProfile p2 = profileBuilder.build("u2", ratings2);

            UserVector center = unitAt(5);
            ClusterModel clusterModel = new ClusterModel(1, List.of(center), List.of(0), 1L, List.of(5.0));

            List<UserProfile> updatedProfiles = analyzer.updateClustersBatch(List.of(p1, p2), clusterModel);

            assertThat(updatedProfiles.get(0).getClusterIndex()).isEqualTo(0);
            assertThat(updatedProfiles.get(1).getClusterIndex()).isEqualTo(0);
        }
    }
}