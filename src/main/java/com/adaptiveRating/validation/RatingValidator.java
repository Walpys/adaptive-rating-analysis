package com.adaptiveRating.validation;


import com.adaptiveRating.exception.rating.RatingValidationException;
import com.adaptiveRating.model.Rating;

import java.util.List;
import java.util.Objects;

public final class RatingValidator {

    private RatingValidator() {
    }

    public static void validateRatingList(List<Rating> ratings, int minRequired) {
        Objects.requireNonNull(ratings, "Ratings list must not be null");
        if (ratings.size() < minRequired) {
            throw new RatingValidationException(
                    String.format("Insufficient ratings: got %d, but at least %d required.", ratings.size(), minRequired)
            );
        }
        for (Rating rating : ratings) {
            Objects.requireNonNull(rating, "Rating item must not be null");
        }
    }

    public static void validateUserHistoryBelongsToUser(String userId, List<Rating> history) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(history, "History list must not be null");

        for (Rating r : history) {
            if (!r.userId().equals(userId)) {
                throw new RatingValidationException(
                        String.format("History contains rating for mismatched userId: expected %s, got %s", userId, r.userId())
                );
            }
        }
    }
}