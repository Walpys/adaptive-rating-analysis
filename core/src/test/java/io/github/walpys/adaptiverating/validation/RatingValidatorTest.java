package io.github.walpys.adaptiverating.validation;

import io.github.walpys.adaptiverating.exception.AppIllegalArgumentException;
import io.github.walpys.adaptiverating.exception.rating.RatingValidationException;
import io.github.walpys.adaptiverating.model.Rating;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.walpys.adaptiverating.TestFixtures.history;
import static io.github.walpys.adaptiverating.TestFixtures.rating;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RatingValidatorTest {

    @Nested
    @DisplayName("validateRatingList tests")
    class ValidateRatingListTests {

        @Test
        @DisplayName("valid rating list passes validation")
        void validListPasses() {
            List<Rating> list = history("u1", 5, 6, 7);
            RatingValidator.validateRatingList(list, 3);
        }

        @Test
        @DisplayName("null rating list throws exception")
        void nullListThrows() {
            assertThatThrownBy(() -> RatingValidator.validateRatingList(null, 3))
                    .isInstanceOf(AppIllegalArgumentException.class)
                    .hasMessageContaining("Ratings list must not be null");
        }

        @Test
        @DisplayName("insufficient ratings count throws RatingValidationException")
        void insufficientRatingsThrows() {
            List<Rating> list = history("u1", 5, 6);
            assertThatThrownBy(() -> RatingValidator.validateRatingList(list, 3))
                    .isInstanceOf(RatingValidationException.class)
                    .hasMessageContaining("Insufficient ratings");
        }

        @Test
        @DisplayName("null item inside rating list throws exception")
        void nullItemThrows() {
            List<Rating> list = new ArrayList<>();
            list.add(rating("u1", 5.0));
            list.add(null);

            assertThatThrownBy(() -> RatingValidator.validateRatingList(list, 2))
                    .isInstanceOf(AppIllegalArgumentException.class)
                    .hasMessageContaining("Rating item must not be null");
        }
    }

    @Nested
    @DisplayName("validateUserHistoryBelongsToUser tests")
    class ValidateUserHistoryBelongsToUserTests {

        @Test
        @DisplayName("matching userId history passes validation")
        void matchingHistoryPasses() {
            List<Rating> list = history("u1", 5, 6, 7);
            RatingValidator.validateUserHistoryBelongsToUser("u1", list);
        }

        @Test
        @DisplayName("null userId or history throws exception")
        void nullArgumentsThrow() {
            assertThatThrownBy(() -> RatingValidator.validateUserHistoryBelongsToUser(null, List.of()))
                    .isInstanceOf(AppIllegalArgumentException.class);
            assertThatThrownBy(() -> RatingValidator.validateUserHistoryBelongsToUser("u1", null))
                    .isInstanceOf(AppIllegalArgumentException.class);
        }

        @Test
        @DisplayName("mismatched userId in history throws RatingValidationException")
        void mismatchedUserIdThrows() {
            List<Rating> list = List.of(
                    rating("u1", 5.0),
                    rating("other-user", 6.0)
            );

            assertThatThrownBy(() -> RatingValidator.validateUserHistoryBelongsToUser("u1", list))
                    .isInstanceOf(RatingValidationException.class)
                    .hasMessageContaining("mismatched userId");
        }
    }

    @Nested
    @DisplayName("isValidRatingList tests")
    class IsValidRatingListTests {

        @Test
        @DisplayName("returns true for valid rating list")
        void returnsTrueForValid() {
            List<Rating> list = history("u1", 5, 6, 7);
            assertThat(RatingValidator.isValidRatingList(list, 3)).isTrue();
        }

        @Test
        @DisplayName("returns false for null, short list, or null items")
        void returnsFalseForInvalid() {
            assertThat(RatingValidator.isValidRatingList(null, 3)).isFalse();
            assertThat(RatingValidator.isValidRatingList(history("u1", 5), 3)).isFalse();

            List<Rating> listWithNull = new ArrayList<>();
            listWithNull.add(rating("u1", 5.0));
            listWithNull.add(null);
            assertThat(RatingValidator.isValidRatingList(listWithNull, 2)).isFalse();
        }
    }

    @Nested
    @DisplayName("validateRatingItem tests")
    class ValidateRatingItemTests {

        @Test
        @DisplayName("non-null rating passes validation")
        void nonNullPasses() {
            RatingValidator.validateRatingItem(rating("u1", 5.0));
        }

        @Test
        @DisplayName("null rating throws exception")
        void nullRatingThrows() {
            assertThatThrownBy(() -> RatingValidator.validateRatingItem(null))
                    .isInstanceOf(AppIllegalArgumentException.class);
        }
    }
}