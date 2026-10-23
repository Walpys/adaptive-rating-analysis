package com.adaptiveRating.model;


import com.adaptiveRating.exception.user.UserValidationException;

import java.util.Map;
import java.util.Objects;

public record UserProfile(
        String userId,
        int totalRatings,
        Map<Integer, Integer> ratingCounts,
        UserVector userVector,
        double mean,
        double median,
        double standardDeviation,
        int minRating,
        int maxRating
) {
    public UserProfile {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(ratingCounts, "ratingCounts must not be null");
        Objects.requireNonNull(userVector, "userVector must not be null");
        if (userId.isBlank()) {
            throw new UserValidationException("userId must not be blank");
        }
        if (totalRatings < 0) {
            throw new UserValidationException("totalRatings cannot be negative");
        }
    }
}