package io.github.walpys.adaptiverating.model;

import io.github.walpys.adaptiverating.exception.rating.RatingValidationException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record Rating(
        String userId,
        String objectId,
        double rating,
        Instant timestamp,
        Instant updatedAt
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
        if (Double.isNaN(rating) || rating < 1.0 || rating > 10.0) {
            throw new RatingValidationException("Rating value must be between 1.0 and 10.0, got: " + rating);
        }
    }

    public Rating(String userId, String objectId, double rating, Instant timestamp) {
        this(userId, objectId, rating, timestamp, null);
    }

    public Optional<Instant> getUpdatedAt() {
        return Optional.ofNullable(updatedAt);
    }

    public Instant getEffectiveTime() {
        return isInitialCreation() ? (updatedAt != null ? updatedAt : timestamp) : timestamp;
    }

    public boolean isInitialCreation() {
        if (updatedAt == null) {
            return true;
        }
        return Duration.between(timestamp, updatedAt).abs().compareTo(Duration.ofSeconds(5)) <= 0;
    }
}