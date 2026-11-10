package io.github.walpys.adaptiverating.api;

import io.github.walpys.adaptiverating.anomaly.*;
import io.github.walpys.adaptiverating.clustering.KMeans;
import io.github.walpys.adaptiverating.config.AnalyzerConfig;
import io.github.walpys.adaptiverating.datasource.RatingDataSource;
import io.github.walpys.adaptiverating.model.*;
import io.github.walpys.adaptiverating.model.enums.AnalysisStatus;
import io.github.walpys.adaptiverating.model.enums.ResultClassification;
import io.github.walpys.adaptiverating.percentile.PercentileCalculator;
import io.github.walpys.adaptiverating.profile.UserProfileBuilder;
import io.github.walpys.adaptiverating.validation.RatingValidator;
import io.github.walpys.adaptiverating.validation.ValidationUtils;

import lombok.Builder;
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

@Getter
public class AdaptiveRatingAnalyzer {

    private static final int USER_BATCH_SIZE = 500;

    private final AnalyzerConfig config;
    private final RatingDataSource dataSource;
    private final UserProfileBuilder profileBuilder;

    private final PersonalAnomalyDetector personalDetector;
    private final ClusterAnomalyDetector clusterDetector;
    private final TemporalAnomalyDetector temporalAnomalyDetector;
    private final AnomalyScoreCalculator anomalyScoreCalculator;
    private final PercentileCalculator percentileCalculator;
    private final DispersionAnomalyDetector dispersionAnomalyDetector;

    private final ConsensusIndex consensusIndex;

    @Getter(lombok.AccessLevel.NONE)
    private final Set<String> trustedUserIds;

    public Set<String> getTrustedUserIds() {
        return Set.copyOf(trustedUserIds);
    }

    @Builder
    public AdaptiveRatingAnalyzer(
            AnalyzerConfig config,
            RatingDataSource dataSource,
            UserProfileBuilder profileBuilder,
            PersonalAnomalyDetector personalDetector,
            ClusterAnomalyDetector clusterDetector,
            TemporalAnomalyDetector temporalAnomalyDetector,
            AnomalyScoreCalculator anomalyScoreCalculator,
            PercentileCalculator percentileCalculator,
            DispersionAnomalyDetector dispersionAnomalyDetector,
            ConsensusIndex consensusIndex,
            ClusterModel clusterModel,
            Integer clusterCount
    ) {
        this.config = config != null ? config : AnalyzerConfig.defaultConfig();
        this.dataSource = Objects.requireNonNull(dataSource, "RatingDataSource must not be null");
        this.profileBuilder = profileBuilder != null ? profileBuilder : new UserProfileBuilder();

        this.personalDetector = personalDetector != null ? personalDetector : new PersonalAnomalyDetector();
        this.clusterDetector = clusterDetector != null ? clusterDetector : new ClusterAnomalyDetector();
        this.temporalAnomalyDetector = temporalAnomalyDetector != null ? temporalAnomalyDetector : TemporalAnomalyDetector.withDefaults();

        this.anomalyScoreCalculator = anomalyScoreCalculator != null
                ? anomalyScoreCalculator
                : new AnomalyScoreCalculator(
                this.config.getWeightPersonal(),
                this.config.getWeightCluster(),
                this.config.getWeightTime(),
                this.config.getWeightObject()
        );

        this.percentileCalculator = percentileCalculator != null ? percentileCalculator : new PercentileCalculator();
        this.dispersionAnomalyDetector = dispersionAnomalyDetector != null ? dispersionAnomalyDetector : new DispersionAnomalyDetector();

        int k = clusterCount != null ? clusterCount : this.config.getKMeansClusters();

        if (consensusIndex != null) {
            this.consensusIndex = consensusIndex;
            this.trustedUserIds = Set.of();
        } else if (clusterModel != null) {
            this.trustedUserIds = buildTrustedUserSet(clusterModel);
            this.consensusIndex = ConsensusIndex.build(this.dataSource, k, this.trustedUserIds::contains);
        } else {
            this.trustedUserIds = Set.of();
            this.consensusIndex = ConsensusIndex.build(this.dataSource, k);
        }
    }

    // ---------------------------------------------------------
    // User Profile Management
    // ---------------------------------------------------------

    public UserProfile buildProfile(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        List<Rating> userRatings = dataSource.getUserRatings(userId);
        RatingValidator.validateRatingList(userRatings, config.getMinRatings());
        return profileBuilder.build(userId, userRatings);
    }

    public UserProfile buildProfile(String userId, List<Rating> ratings) {
        RatingValidator.validateRatingList(ratings, config.getMinRatings());
        return profileBuilder.build(userId, ratings);
    }

    public Map<String, UserProfile> buildProfilesBatch(List<String> userIds) {
        ValidationUtils.requireNonEmpty(userIds, "userIds must not be null or empty");

        Map<String, List<Rating>> batchRatings = dataSource.getUserRatingsBatch(userIds);
        Map<String, UserProfile> resultProfiles = new HashMap<>();

        for (String userId : userIds) {
            List<Rating> userRatings = batchRatings.get(userId);
            if (RatingValidator.isValidRatingList(userRatings, config.getMinRatings())) {
                UserProfile profile = profileBuilder.build(userId, userRatings);
                resultProfiles.put(userId, profile);
            }
        }
        return resultProfiles;
    }

    public UserProfile buildProfileWithCluster(String userId, ClusterModel clusterModel) {
        Objects.requireNonNull(clusterModel, "ClusterModel must not be null");
        UserProfile profile = buildProfile(userId);
        int closestClusterIndex = KMeans.findClosestCluster(profile.getUserVector(), clusterModel);
        return profile.withClusterIndex(closestClusterIndex);
    }

    public UserProfile updateClusterAssignment(UserProfile profile, ClusterModel clusterModel) {
        Objects.requireNonNull(profile, "UserProfile must not be null");
        Objects.requireNonNull(clusterModel, "ClusterModel must not be null");
        int closestClusterIndex = KMeans.findClosestCluster(profile.getUserVector(), clusterModel);
        return profile.withClusterIndex(closestClusterIndex);
    }

    public List<UserProfile> updateClustersBatch(List<UserProfile> profiles, ClusterModel clusterModel) {
        Objects.requireNonNull(profiles, "Profiles collection must not be null");
        Objects.requireNonNull(clusterModel, "ClusterModel must not be null");

        return profiles.stream()
                .filter(Objects::nonNull)
                .map(profile -> {
                    if (profile.getUserVector() != null) {
                        int closestClusterIndex = KMeans.findClosestCluster(profile.getUserVector(), clusterModel);
                        return profile.withClusterIndex(closestClusterIndex);
                    }
                    return profile;
                })
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------
    // Cluster Analysis
    // ---------------------------------------------------------

    public ClusterModel buildClusters(List<UserVector> userVectors) {
        return buildClusters(userVectors, config.getKMeansClusters());
    }

    public ClusterModel buildClusters(List<UserVector> userVectors, int customK) {
        ValidationUtils.requireNonEmpty(userVectors, "userVectors must not be null or empty");
        ValidationUtils.requirePositive(customK, "K must be positive");

        KMeans kMeans = new KMeans(
                customK,
                config.getMaxKMeansIterations(),
                config.getKMeansSeed()
        );
        return kMeans.cluster(userVectors);
    }

    // ---------------------------------------------------------
    // Rating Analysis
    // ---------------------------------------------------------

    public AnalysisResult analyze(AnalysisContext context) {
        Objects.requireNonNull(context, "AnalysisContext must not be null");
        RatingValidator.validateRatingItem(context.rating());
        RatingValidator.validateRatingList(context.userHistory(), config.getMinRatings());
        Objects.requireNonNull(context.profile(), "Profile must not be null");

        var personalPercentile = percentileCalculator.calculatePercentile(
                context.rating(), context.userHistory()
        );

        var personalAnomaly = personalDetector.calculateAnomaly(
                context.rating(), context.profile()
        );

        boolean selfIncluded = trustedUserIds.contains(context.profile().getUserId());
        Integer clusterIndex = context.profile().getClusterIndex();

        double objectConsensusAnomaly = consensusIndex.score(
                context.rating(), clusterIndex, selfIncluded
        );

        if (Double.isNaN(objectConsensusAnomaly)) {
            objectConsensusAnomaly = 0.0;
        }

        var temporalAnomaly = temporalAnomalyDetector.calculateAnomaly(
                context.rating(), context.userHistory()
        );

        double anomalyScore = anomalyScoreCalculator.calculateScore(
                personalAnomaly, null, temporalAnomaly, objectConsensusAnomaly
        );

        String clusterId = resolveClusterId(context.profile(), context.clusterModel());

        Map<String, Double> signalMap = Map.of(
                "personal", personalAnomaly * config.getWeightPersonal(),
                "temporal", temporalAnomaly * config.getWeightTime(),
                "objectConsensus", objectConsensusAnomaly * config.getWeightObject()
        );

        String dominantSignal = evaluateDominantSignal(signalMap);
        AnalysisResolution resolution = determineResolution(anomalyScore);

        return AnalysisResult.builder()
                .rawRating(context.rating())
                .personalPercentile(personalPercentile)
                .clusterId(clusterId)
                .personalAnomaly(personalAnomaly)
                .temporalAnomaly(temporalAnomaly)
                .objectConsensusAnomaly(objectConsensusAnomaly)
                .anomalyScore(anomalyScore)
                .status(resolution.status())
                .classification(resolution.classification())
                .signalMap(signalMap)
                .dominantSignal(dominantSignal)
                .build();
    }

    public AnalysisResult analyze(String userId, Rating rating, ClusterModel clusterModel) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(rating, "rating must not be null");

        UserProfile profile = Objects.requireNonNull(
                dataSource.getUserProfile(userId),
                "UserProfile must exist in data source before analysis!"
        );

        List<Rating> userHistory = dataSource.getUserRatings(userId);
        AnalysisContext context = new AnalysisContext(rating, userHistory, profile, clusterModel);

        return analyze(context);
    }

    // ---------------------------------------------------------
    // Profile Anomaly Analysis
    // ---------------------------------------------------------

    public ProfileAnalysisResult analyzeProfile(String userId, ClusterModel clusterModel) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(clusterModel, "ClusterModel must not be null");

        UserProfile profile = Objects.requireNonNull(
                dataSource.getUserProfile(userId),
                "UserProfile must exist in data source!"
        );

        Double clusterAnomaly = profile.getClusterIndex() != null
                ? clusterDetector.calculateAnomaly(profile, clusterModel)
                : null;

        List<Rating> userRatings = dataSource.getUserRatings(userId);

        Double dispersionAnomaly = dispersionAnomalyDetector.calculateAnomaly(profile);
        Double temporalAnomaly = temporalAnomalyDetector.calculateProfileAnomaly(userRatings);
        boolean selfIncluded = trustedUserIds.contains(userId);

        Double objectConsensusAnomaly = consensusIndex.profileConsensus(
                userRatings, profile.getClusterIndex(), selfIncluded
        );

        double overallScore = anomalyScoreCalculator.calculateProfileScore(
                dispersionAnomaly, clusterAnomaly, temporalAnomaly, objectConsensusAnomaly
        );

        String clusterId = resolveClusterId(profile, clusterModel);

        Map<String, Double> signalMap = Map.of(
                "dispersion", dispersionAnomaly * config.getWeightPersonal(),
                "cluster", clusterAnomaly != null ? clusterAnomaly * config.getWeightCluster() : 0.0,
                "temporal", temporalAnomaly * config.getWeightTime(),
                "objectConsensus", objectConsensusAnomaly * config.getWeightObject()
        );

        String dominantSignal = evaluateDominantSignal(signalMap);
        AnalysisResolution resolution = determineResolution(overallScore);

        return ProfileAnalysisResult.builder()
                .userId(profile.getUserId())
                .totalRatings(profile.getTotalRatings())
                .meanRating(profile.getMean())
                .standardDeviation(profile.getStandardDeviation())
                .clusterId(clusterId)
                .clusterAnomaly(clusterAnomaly)
                .dispersionAnomaly(dispersionAnomaly)
                .temporalAnomaly(temporalAnomaly)
                .objectConsensusAnomaly(objectConsensusAnomaly)
                .overallAnomalyScore(overallScore)
                .status(resolution.status())
                .classification(resolution.classification())
                .signalMap(signalMap)
                .dominantSignal(dominantSignal)
                .build();
    }

    // ---------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------

    private Set<String> buildTrustedUserSet(ClusterModel clusterModel) {
        Objects.requireNonNull(clusterModel, "ClusterModel must not be null");
        Set<String> trusted = new HashSet<>();
        Set<String> allUserIds = dataSource.getAllUserIds();
        List<String> batch = new ArrayList<>(USER_BATCH_SIZE);

        for (String userId : allUserIds) {
            batch.add(userId);
            if (batch.size() >= USER_BATCH_SIZE) {
                processTrustedUserBatch(batch, clusterModel, trusted);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            processTrustedUserBatch(batch, clusterModel, trusted);
        }

        return Set.copyOf(trusted);
    }

    private void processTrustedUserBatch(List<String> userIds, ClusterModel clusterModel, Set<String> trusted) {
        Map<String, List<Rating>> ratingsByUser = dataSource.getUserRatingsBatch(userIds);

        for (String userId : userIds) {
            UserProfile profile = dataSource.getUserProfile(userId);
            if (profile == null) continue;

            List<Rating> userRatings = ratingsByUser.getOrDefault(userId, List.of());

            double dispersion = dispersionAnomalyDetector.calculateAnomaly(profile);
            double temporal = temporalAnomalyDetector.calculateProfileAnomaly(userRatings);
            double cluster = profile.getClusterIndex() != null
                    ? clusterDetector.calculateAnomaly(profile, clusterModel)
                    : 0.0;

            if (dispersion < 0.5 && temporal < 0.5 && cluster < 0.8) {
                trusted.add(userId);
            }
        }
    }

    private String resolveClusterId(UserProfile profile, ClusterModel clusterModel) {
        if (profile.getClusterIndex() != null) {
            return String.valueOf(profile.getClusterIndex());
        }
        if (clusterModel != null && profile.getUserVector() != null) {
            int clusterIndex = KMeans.findClosestCluster(profile.getUserVector(), clusterModel);
            return String.valueOf(clusterIndex);
        }
        return "N/A";
    }

    private String evaluateDominantSignal(Map<String, Double> signalMap) {
        return signalMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("none");
    }

    private AnalysisResolution determineResolution(double anomalyScore) {
        AnalysisStatus status = AnalysisStatus.COMPLETE;
        ResultClassification classification;

        if (anomalyScore >= config.getAnomalyThreshold()) {
            classification = ResultClassification.ANOMALOUS;
        } else if (anomalyScore >= config.getSuspiciousThreshold()) {
            classification = ResultClassification.SUSPICIOUS;
        } else {
            classification = ResultClassification.NORMAL;
        }

        return new AnalysisResolution(status, classification);
    }

    private record AnalysisResolution(AnalysisStatus status, ResultClassification classification) {
    }
}