package com.adaptiveRating.model;

import com.adaptiveRating.exception.rating.RatingValidationException;

import java.time.Instant;
import java.util.Objects;

public record Rating(
        String userId,
        String objectId,
        int rating,
        Instant timestamp
) {
    public Rating {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(objectId, "objectId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");

        if (userId.isBlank()) {
            throw new RatingValidationException("userId must not be blank");
        }
        if (objectId.isBlank()) {
            throw new RatingValidationException("objectId must not be blank");
        }
        if (rating < 1 || rating > 10) {
            throw new RatingValidationException("Rating value must be between 1 and 10, got: " + rating);
        }
    }
}