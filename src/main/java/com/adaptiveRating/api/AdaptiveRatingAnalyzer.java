package com.adaptiveRating.api;

import com.adaptiveRating.anomaly.*;
import com.adaptiveRating.config.AnalyzerConfig;
import com.adaptiveRating.datasource.RatingDataSource;
import com.adaptiveRating.exception.cluster.ClusterNotFoundException;
import com.adaptiveRating.model.*;
import com.adaptiveRating.model.enums.AnalysisStatus;
import com.adaptiveRating.model.enums.ResultClassification;
import com.adaptiveRating.percentile.PercentileCalculator;
import com.adaptiveRating.profile.UserProfileBuilder;
import com.adaptiveRating.validation.RatingValidator;
import com.adaptiveRating.validation.ValidationUtils;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class AdaptiveRatingAnalyzer {

    private final AnalyzerConfig config;
    private final UserProfileBuilder profileBuilder = new UserProfileBuilder();
    private final PercentileCalculator percentileCalculator = new PercentileCalculator();
    private final PersonalAnomalyDetector personalDetector = new PersonalAnomalyDetector();
    private final ClusterAnomalyDetector clusterDetector = new ClusterAnomalyDetector();
    private final ObjectConsensusAnomalyDetector objectConsensusAnomalyDetector = new ObjectConsensusAnomalyDetector();

    public AnalysisResult analyze(Rating rating, RatingDataSource dataSource, ClusterModel clusterModel, int clusterIndex) {
        ValidationUtils.requireNonNull(rating, "rating must not be null");
        ValidationUtils.requireNonNull(dataSource, "dataSource must not be null");

        List<Rating> history = dataSource.getUserRatings(rating.userId());
        return analyzeInternal(rating, history, dataSource, clusterModel, clusterIndex);
    }

    public AnalysisResult analyze(Rating rating, List<Rating> userHistory, ClusterModel clusterModel, int clusterIndex) {
        ValidationUtils.requireNonNull(rating, "rating must not be null");
        ValidationUtils.requireNonNull(userHistory, "userHistory must not be null");

        RatingValidator.validateUserHistoryBelongsToUser(rating.userId(), userHistory);

        return analyzeInternal(rating, userHistory, null, clusterModel, clusterIndex);
    }

    private AnalysisResult analyzeInternal(Rating rating, List<Rating> history, RatingDataSource dataSource, ClusterModel clusterModel, int clusterIndex) {
        if (history.size() < config.getMinRatings()) {
            return new AnalysisResult(
                    rating, null, null, null, null, null, null,
                    AnalysisStatus.INSUFFICIENT_DATA, null
            );
        }

        UserProfile profile = profileBuilder.build(rating.userId(), history);
        double percentile = percentileCalculator.calculatePercentile(rating, history);
        double personalAnomaly = personalDetector.calculateAnomaly(rating, profile);

        TemporalAnomalyDetector temporalDetector = new TemporalAnomalyDetector(
                config.getTemporalWindow(), config.getMaxTemporalCountInWindow()
        );
        double temporalAnomaly = temporalDetector.calculateAnomaly(rating, history);

        List<Rating> objectRatings = (dataSource != null)
                ? dataSource.getObjectRatings(rating.objectId())
                : history;

        double objectConsensusAnomaly = objectConsensusAnomalyDetector.calculateAnomaly(rating, objectRatings);

        Double clusterAnomaly = null;
        String clusterIdStr = null;
        if (clusterModel != null && clusterIndex >= 0) {
            if (clusterIndex >= clusterModel.getClusterCenters().size()) {
                throw new ClusterNotFoundException("Invalid cluster index: " + clusterIndex);
            }
            clusterAnomaly = clusterDetector.calculateAnomaly(profile.userVector(), clusterModel, clusterIndex);
            clusterIdStr = "Cluster-" + clusterIndex;
        }

        AnomalyScoreCalculator scoreCalculator = new AnomalyScoreCalculator(
                config.getWeightPersonal(),
                config.getWeightCluster(),
                config.getWeightTime(),
                config.getWeightObject()
        );

        Optional<Double> anomalyScoreOpt = scoreCalculator.calculateScore(
                Optional.of(personalAnomaly),
                Optional.ofNullable(clusterAnomaly),
                Optional.of(temporalAnomaly),
                Optional.of(objectConsensusAnomaly)
        );

        AnalysisStatus status = (clusterModel != null && clusterIndex >= 0) ? AnalysisStatus.COMPLETE : AnalysisStatus.PARTIAL;

        ResultClassification classification = null;
        if (anomalyScoreOpt.isPresent()) {
            classification = (anomalyScoreOpt.get() >= config.getAnomalyThreshold())
                    ? ResultClassification.ANOMALOUS
                    : ResultClassification.NORMAL;
        }

        return new AnalysisResult(
                rating,
                percentile,
                clusterIdStr,
                personalAnomaly,
                clusterAnomaly,
                temporalAnomaly,
                anomalyScoreOpt.orElse(null),
                status,
                classification
        );
    }
}