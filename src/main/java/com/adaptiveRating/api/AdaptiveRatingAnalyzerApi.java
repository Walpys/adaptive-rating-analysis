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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final DispersionAnomalyDetector dispersionAnomalyDetector;

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
     * Оптимізований метод для масового/великого датасету.
     * Автоматично створює глобальний кеш статистик об'єктів (або приймає його готовим),
     * щоб уникнути мільйонів повторних перерахунків mean та std у циклах.
     */
    public ProfileAnalysisResult analyzeProfileForLargeData(String userId, ClusterModel clusterModel) {
        // Генеруємо кеш «на льоту» для конкретного виклику (або можна передати спільний мапом вище)
        Map<String, ObjectConsensusAnomalyDetector.ObjectStats> globalCache = buildGlobalStatsCache();
        return analyzeProfileWithCache(userId, clusterModel, globalCache);
    }

    public ProfileAnalysisResult analyzeProfileWithCache(
            String userId,
            ClusterModel clusterModel,
            Map<String, ObjectConsensusAnomalyDetector.ObjectStats> globalStatsCache
    ) {
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

        // Використовуємо оптимізований метод з кешем об'єктів
        Double objectConsensusAnomaly = objectConsensusAnomalyDetector.calculateProfileAnomalyWithCache(
                userRatings,
                objectId -> profile.getClusterIndex() != null ? dataSource.getObjectRatingsInCluster(objectId, profile.getClusterIndex()) : null,
                dataSource::getObjectRatings,
                globalStatsCache
        );

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
     * Допоміжний метод для попереднього збору статистик по всіх унікальних об'єктах у джерелі даних.
     */
    public Map<String, ObjectConsensusAnomalyDetector.ObjectStats> buildGlobalStatsCache() {
        Map<String, ObjectConsensusAnomalyDetector.ObjectStats> cache = new HashMap<>();
        // Отримуємо всі унікальні об'єкти з dataSource (якщо твій datasource підтримує це,
        // або мапа будується динамічно за потребою)
        for (String objectId : dataSource.getAllObjectIds()) {
            List<Rating> ratings = dataSource.getObjectRatings(objectId);
            if (ratings != null && !ratings.isEmpty()) {
                // null як excludeUserId, оскільки це загальна глобальна статистика об'єкта
                ObjectConsensusAnomalyDetector.ObjectStats stats =
                        ObjectConsensusAnomalyDetector.ObjectStats.fromRatings(ratings, null);
                if (stats != null) {
                    cache.put(objectId, stats);
                }
            }
        }
        return cache;
    }

    /**
     * Оптимізований метод для великих даних, який приймає вже готовий глобальний кеш.
     */
    public ProfileAnalysisResult analyzeProfileForLargeData(
            String userId,
            ClusterModel clusterModel,
            Map<String, ObjectConsensusAnomalyDetector.ObjectStats> globalCache
    ) {
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

        // Використовуємо кеш
        Double objectConsensusAnomaly = objectConsensusAnomalyDetector.calculateProfileAnomalyWithCache(
                userRatings,
                objectId -> profile.getClusterIndex() != null ? dataSource.getObjectRatingsInCluster(objectId, profile.getClusterIndex()) : null,
                dataSource::getObjectRatings,
                globalCache
        );

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

    public ProfileAnalysisResult analyzeProfile(String userId, ClusterModel clusterModel) {
        // Для звичайного використання можна напряму спрямувати на великий датасет з кешем
        return analyzeProfileForLargeData(userId, clusterModel);
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