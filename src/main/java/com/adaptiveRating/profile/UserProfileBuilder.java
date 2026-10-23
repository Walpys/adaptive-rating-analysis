package com.adaptiveRating.profile;

import com.adaptiveRating.exception.rating.RatingValidationException;
import com.adaptiveRating.model.Rating;
import com.adaptiveRating.model.UserProfile;
import com.adaptiveRating.model.UserVector;
import com.adaptiveRating.validation.RatingValidator;

import java.util.*;

public class UserProfileBuilder {

    private final int minRatings;

    public UserProfileBuilder(int minRatings) {
        if (minRatings < 1) {
            throw new RatingValidationException("minRatings must be at least 1");
        }
        this.minRatings = minRatings;
    }

    public UserProfile build(String userId, List<Rating> ratings) {
        Objects.requireNonNull(userId, "userId must not be null");
        RatingValidator.validateRatingList(ratings, minRatings);
        RatingValidator.validateUserHistoryBelongsToUser(userId, ratings);

        int totalRatings = ratings.size();
        List<Integer> values = extractRatingValues(ratings);

        Map<Integer, Integer> ratingCounts = calculateRatingCounts(values);
        double mean = calculateMean(values, totalRatings);
        double median = calculateMedian(values, totalRatings);
        double standardDeviation = calculateStandardDeviation(values, mean, totalRatings);

        int min = Collections.min(values);
        int max = Collections.max(values);

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
                max
        );
    }

    private List<Integer> extractRatingValues(List<Rating> ratings) {
        List<Integer> values = new ArrayList<>(ratings.size());
        for (Rating r : ratings) {
            values.add(r.rating());
        }
        return values;
    }

    private Map<Integer, Integer> calculateRatingCounts(List<Integer> values) {
        Map<Integer, Integer> ratingCounts = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            ratingCounts.put(i, 0);
        }
        for (int val : values) {
            ratingCounts.put(val, ratingCounts.get(val) + 1);
        }
        return ratingCounts;
    }

    private double calculateMean(List<Integer> values, int totalRatings) {
        double sum = 0;
        for (int val : values) {
            sum += val;
        }
        return sum / totalRatings;
    }

    private double calculateMedian(List<Integer> values, int totalRatings) {
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        int middle = totalRatings / 2;
        if (totalRatings % 2 == 0) {
            return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
        } else {
            return sorted.get(middle);
        }
    }

    private double calculateStandardDeviation(List<Integer> values, double mean, int totalRatings) {
        double varianceSum = 0;
        for (int val : values) {
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