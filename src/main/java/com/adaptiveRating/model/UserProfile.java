package com.adaptiveRating.model;

import com.adaptiveRating.exception.user.UserValidationException;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.Objects;

@Getter
@Setter
public final class UserProfile {
    private String userId;
    private int totalRatings;
    private Map<Integer, Integer> ratingCounts;
    private UserVector userVector;
    private double mean;
    private double median;
    private double standardDeviation;
    private double minRating;
    private double maxRating;
    private Integer clusterIndex;

    public UserProfile(String userId, int totalRatings, Map<Integer, Integer> ratingCounts, UserVector userVector, double mean, double median, double standardDeviation, double minRating, double maxRating, Integer clusterIndex) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(ratingCounts, "ratingCounts must not be null");
        Objects.requireNonNull(userVector, "userVector must not be null");
        if (userId.isBlank()) {
            throw new UserValidationException("userId must not be blank");
        }
        if (totalRatings < 0) {
            throw new UserValidationException("totalRatings cannot be negative");
        }
        this.userId = userId;
        this.totalRatings = totalRatings;
        this.ratingCounts = Map.copyOf(ratingCounts);
        this.userVector = userVector;
        this.mean = mean;
        this.median = median;
        this.standardDeviation = standardDeviation;
        this.minRating = minRating;
        this.maxRating = maxRating;
        this.clusterIndex = clusterIndex;
    }

    public UserProfile(UserProfile other) {
        Objects.requireNonNull(other, "other profile must not be null");
        this.userId = other.userId;
        this.totalRatings = other.totalRatings;
        this.ratingCounts = other.ratingCounts != null ? Map.copyOf(other.ratingCounts) : null;
        this.userVector = other.userVector;
        this.mean = other.mean;
        this.median = other.median;
        this.standardDeviation = other.standardDeviation;
        this.minRating = other.minRating;
        this.maxRating = other.maxRating;
        this.clusterIndex = other.clusterIndex;
    }

    public Map<Integer, Integer> getRatingCounts() {
        return ratingCounts != null ? Map.copyOf(ratingCounts) : null;
    }

    public void setRatingCounts(Map<Integer, Integer> ratingCounts) {
        Objects.requireNonNull(ratingCounts, "ratingCounts must not be null");
        this.ratingCounts = Map.copyOf(ratingCounts);
    }
}