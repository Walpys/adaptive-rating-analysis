package com.adaptiveRating.validation;

import com.adaptiveRating.exception.AppIllegalArgumentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationUtilsTest {

    @Test
    @DisplayName("requireNonNull повертає той самий об'єкт")
    void requireNonNullReturnsValue() {
        String value = "ok";

        assertThat(ValidationUtils.requireNonNull(value, "must not be null")).isSameAs(value);
    }

    @Test
    @DisplayName("requireNonNull кидає AppIllegalArgumentException")
    void requireNonNullThrows() {
        assertThatThrownBy(() -> ValidationUtils.requireNonNull(null, "rating must not be null"))
                .isInstanceOf(AppIllegalArgumentException.class)
                .hasMessage("rating must not be null");
    }

    @Test
    @DisplayName("requirePositive відхиляє 0 і від'ємні")
    void requirePositiveRejectsNonPositive() {
        assertThatThrownBy(() -> ValidationUtils.requirePositive(0, "K must be positive"))
                .isInstanceOf(AppIllegalArgumentException.class);
        assertThatThrownBy(() -> ValidationUtils.requirePositive(-2.0, "weight must be positive"))
                .isInstanceOf(AppIllegalArgumentException.class);
    }

    @Test
    @DisplayName("requireNonEmpty відхиляє null і порожню колекцію")
    void requireNonEmptyRejectsNullAndEmpty() {
        assertThatThrownBy(() -> ValidationUtils.requireNonEmpty(null, "userVectors must not be null or empty"))
                .isInstanceOf(AppIllegalArgumentException.class);
        assertThatThrownBy(() -> ValidationUtils.requireNonEmpty(List.of(), "userVectors must not be null or empty"))
                .isInstanceOf(AppIllegalArgumentException.class);
    }
}
