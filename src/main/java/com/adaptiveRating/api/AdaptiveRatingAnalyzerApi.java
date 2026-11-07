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

public class AdaptiveRatingAnalyzerApi {

    private final AnalyzerConfig config;
    private final RatingDataSource dataSource;
    private final PersonalAnomalyDetector personalDetector;
    private final ClusterAnomalyDetector clusterDetector;
    private final ObjectConsensusAnomalyDetector objectConsensusAnomalyDetector;
    private final TemporalAnomalyDetector temporalAnomalyDetector;
    private final AnomalyScoreCalculator anomalyScoreCalculator;
    private final PercentileCalculator percentileCalculator;
    private final DispersionAnomalyDetector dispersionAnomalyDetector;
    private final ConsensusIndex consensusIndex;

    /**
     * Повний конструктор (якщо індекс побудовано ззовні).
     */
    public AdaptiveRatingAnalyzerApi(
            AnalyzerConfig config,
            RatingDataSource dataSource,
            PersonalAnomalyDetector personalDetector,
            ClusterAnomalyDetector clusterDetector,
            ObjectConsensusAnomalyDetector objectConsensusAnomalyDetector,
            TemporalAnomalyDetector temporalAnomalyDetector,
            AnomalyScoreCalculator anomalyScoreCalculator,
            PercentileCalculator percentileCalculator,
            DispersionAnomalyDetector dispersionAnomalyDetector,
            ConsensusIndex consensusIndex
    ) {
        this.config = config;
        this.dataSource = dataSource;
        this.personalDetector = personalDetector;
        this.clusterDetector = clusterDetector;
        this.objectConsensusAnomalyDetector = objectConsensusAnomalyDetector;
        this.temporalAnomalyDetector = temporalAnomalyDetector;
        this.anomalyScoreCalculator = anomalyScoreCalculator;
        this.percentileCalculator = percentileCalculator;
        this.dispersionAnomalyDetector = dispersionAnomalyDetector;
        this.consensusIndex = consensusIndex;
    }

    /**
     * Зручний конструктор: приймає кількість кластерів (k) і САМ будує ConsensusIndex усередині!
     */
    public AdaptiveRatingAnalyzerApi(
            AnalyzerConfig config,
            RatingDataSource dataSource,
            PersonalAnomalyDetector personalDetector,
            ClusterAnomalyDetector clusterDetector,
            ObjectConsensusAnomalyDetector objectConsensusAnomalyDetector,
            TemporalAnomalyDetector temporalAnomalyDetector,
            AnomalyScoreCalculator anomalyScoreCalculator,
            PercentileCalculator percentileCalculator,
            DispersionAnomalyDetector dispersionAnomalyDetector,
            int clusterCount
    ) {
        this.config = config;
        this.dataSource = dataSource;
        this.personalDetector = personalDetector;
        this.clusterDetector = clusterDetector;
        this.objectConsensusAnomalyDetector = objectConsensusAnomalyDetector;
        this.temporalAnomalyDetector = temporalAnomalyDetector;
        this.anomalyScoreCalculator = anomalyScoreCalculator;
        this.percentileCalculator = percentileCalculator;
        this.dispersionAnomalyDetector = dispersionAnomalyDetector;
        // Автоматична побудова індексу самою лібою
        this.consensusIndex = ConsensusIndex.build(dataSource, clusterCount);
    }

    public AnalysisResult analyze(String userId, Rating rating, ClusterModel clusterModel) {
        UserProfile profile = dataSource.getUserProfile(userId);
        Objects.requireNonNull(profile, "UserProfile must exist in data source before analysis!");

        List<Rating> userHistory = dataSource.getUserRatings(userId);

        Integer clusterIndex = profile.getClusterIndex();
        List<Rating> objectRatingsInCluster = clusterIndex != null
                ? dataSource.getObjectRatingsInCluster(rating.objectId(), clusterIndex)
                : List.of();

        List<Rating> globalObjectRatings = dataSource.getObjectRatings(rating.objectId());

        AnalysisContext context = new AnalysisContext(
                rating,
                userHistory,
                profile,
                clusterModel,
                objectRatingsInCluster,
                globalObjectRatings
        );

        return analyze(context);
    }

    /**
     * Швидкий масовий аналіз профілю з використанням ConsensusIndex O(1).
     */
    public ProfileAnalysisResult analyzeProfileForLargeData(String userId, ClusterModel clusterModel) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(clusterModel, "ClusterModel must not be null");

        UserProfile profile = dataSource.getUserProfile(userId);
        Objects.requireNonNull(profile, "UserProfile must exist in data source!");

        Double clusterAnomaly = null;
        if (profile.getClusterIndex() != null) {
            clusterAnomaly = clusterDetector.calculateAnomaly(profile, clusterModel);
        }

        List<Rating> userRatings = dataSource.getUserRatings(userId);
        Double dispersionAnomaly = dispersionAnomalyDetector.calculateAnomaly(profile);
        Double temporalAnomaly = temporalAnomalyDetector.calculateProfileAnomaly(userRatings);

        // Використовуємо надшвидкий консенсус за O(1)
        Double objectConsensusAnomaly = consensusIndex.profileConsensus(userRatings, profile.getClusterIndex());

        double overallScore = anomalyScoreCalculator.calculateProfileScore(
                dispersionAnomaly,
                clusterAnomaly,
                temporalAnomaly,
                objectConsensusAnomaly
        );

        String clusterId = "N/A";
        if (profile.getClusterIndex() != null) {
            clusterId = String.valueOf(profile.getClusterIndex());
        } else if (profile.getUserVector() != null) {
            int userClusterIndex = KMeans.findClosestCluster(profile.getUserVector(), clusterModel);
            clusterId = String.valueOf(userClusterIndex);
        }

        ResultClassification classification = overallScore >= config.getAnomalyThreshold()
                ? ResultClassification.ANOMALOUS
                : ResultClassification.NORMAL;

        return new ProfileAnalysisResult(
                profile.getUserId(),
                profile.getTotalRatings(),
                profile.getMean(),
                profile.getStandardDeviation(),
                clusterId,
                clusterAnomaly,
                dispersionAnomaly,
                temporalAnomaly,
                objectConsensusAnomaly,
                overallScore,
                AnalysisStatus.COMPLETE,
                classification
        );
    }

    /**
     * Делегуємо звичайний метод аналізу профілю на оптимізовану версію.
     */
    public ProfileAnalysisResult analyzeProfile(String userId, ClusterModel clusterModel) {
        return analyzeProfileForLargeData(userId, clusterModel);
    }

    public AnalysisResult analyze(AnalysisContext context) {
        RatingValidator.validateRatingItem(context.rating());
        RatingValidator.validateRatingList(context.userHistory(), config.getMinRatings());
        Objects.requireNonNull(context.profile(), "Profile must not be null");

        var personalPercentile = percentileCalculator.calculatePercentile(context.rating(), context.userHistory());
        var personalAnomaly = personalDetector.calculateAnomaly(context.rating(), context.profile());

        var objectConsensusAnomaly = objectConsensusAnomalyDetector.calculateAnomaly(
                context.rating(),
                context.objectRatingsInCluster(),
                context.objectRatings()
        );
        var temporalAnomaly = temporalAnomalyDetector.calculateAnomaly(context.rating(), context.userHistory());

        double anomalyScore = anomalyScoreCalculator.calculateScore(
                personalAnomaly, null, temporalAnomaly, objectConsensusAnomaly
        );

        String clusterId = "N/A";
        if (context.profile().getClusterIndex() != null) {
            clusterId = String.valueOf(context.profile().getClusterIndex());
        } else if (context.clusterModel() != null && context.profile().getUserVector() != null) {
            int userClusterIndex = KMeans.findClosestCluster(context.profile().getUserVector(), context.clusterModel());
            clusterId = String.valueOf(userClusterIndex);
        }

        AnalysisResolution resolution = determineResolution(anomalyScore);

        return new AnalysisResult(
                context.rating(),
                personalPercentile,
                clusterId,
                personalAnomaly,
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