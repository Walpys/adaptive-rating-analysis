package io.github.walpys.adaptiverating.profile;

import io.github.walpys.adaptiverating.model.Rating;
import io.github.walpys.adaptiverating.model.UserProfile;
import io.github.walpys.adaptiverating.model.UserVector;

import java.util.*;

public class UserProfileBuilder {

    public UserProfile build(String userId, List<Rating> ratings) {
        int totalRatings = ratings.size();
        List<Double> values = extractRatingValues(ratings);

        Map<Integer, Integer> ratingCounts = calculateRatingCounts(values);
        double mean = calculateMean(values, totalRatings);
        double median = calculateMedian(values, totalRatings);
        double standardDeviation = calculateStandardDeviation(values, mean, totalRatings);

        double min = Collections.min(values);
        double max = Collections.max(values);

        UserVector userVector = buildUserVector(ratingCounts, totalRatings);
        return new UserProfile(
                userId,
                totalRatings,
                ratingCounts,
                userVector,
                mean,
                median,
                standardDeviation,
                min,
                max,
                null
        );
    }

    private List<Double> extractRatingValues(List<Rating> ratings) {
        List<Double> values = new ArrayList<>(ratings.size());
        for (Rating r : ratings) {
            values.add(r.rating());
        }
        return values;
    }

    private Map<Integer, Integer> calculateRatingCounts(List<Double> values) {
        Map<Integer, Integer> ratingCounts = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            ratingCounts.put(i, 0);
        }
        for (double val : values) {
            int bucket = (int) Math.floor(val - 0.5) + 1;
            int clampedBucket = Math.clamp(bucket, 1, 10);
            ratingCounts.put(clampedBucket, ratingCounts.get(clampedBucket) + 1);
        }
        return ratingCounts;
    }

    private double calculateMean(List<Double> values, int totalRatings) {
        double sum = 0;
        for (double val : values) {
            sum += val;
        }
        return sum / totalRatings;
    }

    private double calculateMedian(List<Double> values, int totalRatings) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        int middle = totalRatings / 2;
        if (totalRatings % 2 == 0) {
            return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
        } else {
            return sorted.get(middle);
        }
    }

    private double calculateStandardDeviation(List<Double> values, double mean, int totalRatings) {
        double varianceSum = 0;
        for (double val : values) {
            varianceSum += Math.pow(val - mean, 2);
        }
        return Math.sqrt(varianceSum / totalRatings);
    }

    private UserVector buildUserVector(Map<Integer, Integer> ratingCounts, int totalRatings) {
        double[] vectorComponents = new double[10];
        for (int i = 1; i <= 10; i++) {
            vectorComponents[i - 1] = (double) ratingCounts.get(i) / totalRatings;
        }
        return new UserVector(vectorComponents);
    }
}