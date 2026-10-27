package com.adaptiveRating.api;

import com.adaptiveRating.anomaly.*;
import com.adaptiveRating.clustering.KMeans;
import com.adaptiveRating.config.AnalyzerConfig;
import com.adaptiveRating.datasource.RatingDataSource;
import com.adaptiveRating.model.*;
import com.adaptiveRating.model.enums.AnalysisStatus;
import com.adaptiveRating.model.enums.ResultClassification;
import com.adaptiveRating.percentile.PercentileCalculator;
import com.adaptiveRating.validation.RatingValidator;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
public class AdaptiveRatingAnalyzerApi {

    private final AnalyzerConfig config;
    private final RatingDataSource dataSource;
    private final PersonalAnomalyDetector personalDetector;
    private final ClusterAnomalyDetector clusterDetector;
    private final ObjectConsensusAnomalyDetector objectConsensusAnomalyDetector;
    private final TemporalAnomalyDetector temporalAnomalyDetector;
    private final AnomalyScoreCalculator anomalyScoreCalculator;
    private final PercentileCalculator percentileCalculator;


    public AnalysisResult analyze(String userId, Rating rating, ClusterModel clusterModel) {
        UserProfile profile = dataSource.getUserProfile(userId);
        Objects.requireNonNull(profile, "UserProfile must exist in data source before analysis!");

        List<Rating> userHistory = dataSource.getUserRatings(userId);
        List<Rating> objectRatings = dataSource.getObjectRatings(rating.objectId());

        AnalysisContext context = new AnalysisContext(
                rating,
                userHistory,
                profile,
                clusterModel,
                objectRatings
        );

        return analyze(context);
    }

    public AnalysisResult analyze(AnalysisContext context) {
        RatingValidator.validateRatingItem(context.rating());
        RatingValidator.validateRatingList(context.userHistory(), config.getMinRatings());
        Objects.requireNonNull(context.profile(), "Profile must not be null");

        var personalPercentile = percentileCalculator.calculatePercentile(context.rating(), context.userHistory());
        var personalAnomaly = personalDetector.calculateAnomaly(context.rating(), context.profile());
        var clusterAnomaly = clusterDetector.calculateAnomaly(context.profile(), context.clusterModel());
        var objectConsensusAnomaly = objectConsensusAnomalyDetector.calculateAnomaly(context.rating(), context.objectRatingsInCluster());
        var temporalAnomaly = temporalAnomalyDetector.calculateAnomaly(context.rating(), context.userHistory());

        double anomalyScore = anomalyScoreCalculator.calculateScore(
                personalAnomaly, clusterAnomaly, temporalAnomaly, objectConsensusAnomaly
        );

        int userClusterIndex = KMeans.findClosestCluster(context.profile().getUserVector(), context.clusterModel());
        String clusterId = String.valueOf(userClusterIndex);

        AnalysisResolution resolution = determineResolution(anomalyScore);

        return new AnalysisResult(
                context.rating(),
                personalPercentile,
                clusterId,
                personalAnomaly,
                clusterAnomaly,
                temporalAnomaly,
                objectConsensusAnomaly,
                anomalyScore,
                resolution.status(),
                resolution.classification()
        );
    }

    private AnalysisResolution determineResolution(double anomalyScore) {
        boolean isAnomalous = anomalyScore >= config.getAnomalyThreshold();
        AnalysisStatus status = AnalysisStatus.COMPLETE;
        ResultClassification classification = isAnomalous
                ? ResultClassification.ANOMALOUS
                : ResultClassification.NORMAL;

        return new AnalysisResolution(status, classification);
    }

    private record AnalysisResolution(
            AnalysisStatus status,
            ResultClassification classification
    ) {
    }
}