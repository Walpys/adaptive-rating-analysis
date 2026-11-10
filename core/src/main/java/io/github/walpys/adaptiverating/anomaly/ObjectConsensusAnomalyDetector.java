package io.github.walpys.adaptiverating.anomaly;

import io.github.walpys.adaptiverating.model.Rating;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ObjectConsensusAnomalyDetector {
    private static final int MIN_OTHER_RATINGS = 5;
    private static final double MIN_STD_DEV = 1.0;
    private static final double Z_SCORE_THRESHOLD = 2.0;

    public record ObjectStats(double mean, double std, int count) {
        public static ObjectStats fromRatings(List<Rating> ratings, String excludeUserId) {
            if (ratings == null || ratings.isEmpty()) {
                return null;
            }
            double sum = 0.0;
            int n = 0;

            for (Rating r : ratings) {
                if (excludeUserId != null && r.userId().equals(excludeUserId)) {
                    continue;
                }
                double val = r.rating();
                sum += val;
                n++;
            }

            if (n < MIN_OTHER_RATINGS) {
                return null;
            }

            double meanVal = sum / n;
            return calculateStatsOptimized(ratings, excludeUserId, meanVal, n);
        }

        private static ObjectStats calculateStatsOptimized(List<Rating> ratings, String excludeUserId, double mean, int n) {
            double ss = 0.0;
            for (Rating r : ratings) {
                if (excludeUserId != null && r.userId().equals(excludeUserId)) {
                    continue;
                }
                double diff = r.rating() - mean;
                ss += diff * diff;
            }
            double stdVal = Math.max(Math.sqrt(ss / (n - 1)), MIN_STD_DEV);
            return new ObjectStats(mean, stdVal, n);
        }
    }

    public double calculateAnomalyWithStats(Rating current, ObjectStats stats) {
        if (stats == null || stats.count() < MIN_OTHER_RATINGS) {
            return 0.0;
        }

        double z = Math.abs(current.rating() - stats.mean()) / stats.std();
        return Math.min(1.0, Math.max(0.0, (z - 1.0) / 2.0));
    }

    public double calculateAnomaly(Rating current, List<Rating> objectRatingsInCluster, List<Rating> globalObjectRatings) {
        List<Rating> ratingsToUse = objectRatingsInCluster;
        int otherCountInCluster = countOtherRatings(current, objectRatingsInCluster);

        if (otherCountInCluster < MIN_OTHER_RATINGS && globalObjectRatings != null && !globalObjectRatings.isEmpty()) {
            ratingsToUse = globalObjectRatings;
        }

        if (ratingsToUse == null) return 0.0;

        ObjectStats stats = ObjectStats.fromRatings(ratingsToUse, current.userId());
        return calculateAnomalyWithStats(current, stats);
    }

    public double calculateAnomaly(Rating current, List<Rating> objectRatingsInCluster) {
        return calculateAnomaly(current, objectRatingsInCluster, null);
    }

    public double calculateProfileAnomalyWithCache(
            List<Rating> userHistory,
            Function<String, List<Rating>> clusterRatingsProvider,
            Function<String, List<Rating>> globalRatingsProvider,
            Map<String, ObjectStats> globalStatsCache
    ) {
        if (userHistory == null || userHistory.isEmpty()) {
            return 0.0;
        }

        int evaluatedCount = 0;
        int anomalousCount = 0;
        double maxZScoreSignal = 0.0;

        for (Rating rating : userHistory) {
            String objId = rating.objectId();
            ObjectStats stats;

            List<Rating> clusterRatings = clusterRatingsProvider != null ? clusterRatingsProvider.apply(objId) : null;
            int otherInCluster = countOtherRatings(rating, clusterRatings);

            if (otherInCluster >= MIN_OTHER_RATINGS && clusterRatings != null) {
                stats = ObjectStats.fromRatings(clusterRatings, rating.userId());
            } else {
                if (globalStatsCache != null && globalStatsCache.containsKey(objId)) {
                    stats = globalStatsCache.get(objId);
                } else {
                    List<Rating> globalRatings = globalRatingsProvider != null ? globalRatingsProvider.apply(objId) : null;
                    stats = ObjectStats.fromRatings(globalRatings, rating.userId());
                }
            }

            if (stats != null) {
                evaluatedCount++;
                double score = calculateAnomalyWithStats(rating, stats);
                double estimatedZ = score * 2.0 + 1.0;

                if (estimatedZ >= Z_SCORE_THRESHOLD) {
                    anomalousCount++;
                }
                maxZScoreSignal = Math.max(maxZScoreSignal, score);
            }
        }

        if (evaluatedCount == 0) {
            return 0.0;
        }

        double anomalyRatio = (double) anomalousCount / evaluatedCount;
        double profileConsensusScore = (0.7 * anomalyRatio) + (0.3 * maxZScoreSignal);

        return Math.max(0.0, Math.min(1.0, profileConsensusScore));
    }

    public double calculateProfileAnomaly(
            List<Rating> userHistory,
            Function<String, List<Rating>> clusterRatingsProvider,
            Function<String, List<Rating>> globalRatingsProvider
    ) {
        return calculateProfileAnomalyWithCache(userHistory, clusterRatingsProvider, globalRatingsProvider, null);
    }

    private int countOtherRatings(Rating current, List<Rating> ratings) {
        if (ratings == null) return 0;
        int count = 0;
        for (Rating r : ratings) {
            if (!r.userId().equals(current.userId())) {
                count++;
            }
        }
        return count;
    }
}