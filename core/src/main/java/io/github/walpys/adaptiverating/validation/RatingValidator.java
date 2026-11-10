package io.github.walpys.adaptiverating.validation;

import io.github.walpys.adaptiverating.exception.rating.RatingValidationException;
import io.github.walpys.adaptiverating.model.Rating;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RatingValidator {

    public static void validateRatingList(List<Rating> ratings, int minRequired) {
        ValidationUtils.requireNonNull(ratings, "Ratings list must not be null");
        if (ratings.size() < minRequired) {
            throw new RatingValidationException(
                    String.format("Insufficient ratings: got %d, but at least %d required.", ratings.size(), minRequired)
            );
        }
        for (Rating rating : ratings) {
            ValidationUtils.requireNonNull(rating, "Rating item must not be null");
        }
    }

    public static void validateUserHistoryBelongsToUser(String userId, List<Rating> history) {
        ValidationUtils.requireNonNull(userId, "userId must not be null");
        ValidationUtils.requireNonNull(history, "History list must not be null");

        for (Rating r : history) {
            if (!r.userId().equals(userId)) {
                throw new RatingValidationException(
                        String.format("History contains rating for mismatched userId: expected %s, got %s", userId, r.userId())
                );
            }
        }
    }

    public static boolean isValidRatingList(List<Rating> ratings, int minRequired) {
        if (ratings == null || ratings.size() < minRequired) {
            return false;
        }
        for (Rating rating : ratings) {
            if (rating == null) {
                return false;
            }
        }
        return true;
    }

    public static void validateRatingItem(Rating rating) {
        ValidationUtils.requireNonNull(rating, "Ratings item must not be null");
    }
}