package io.github.walpys.adaptiverating.validation;

import io.github.walpys.adaptiverating.exception.AppIllegalArgumentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationUtilsTest {

    @Test
    @DisplayName("requireNonNull returns the exact same object")
    void requireNonNullReturnsValue() {
        String value = "ok";

        assertThat(ValidationUtils.requireNonNull(value, "must not be null")).isSameAs(value);
    }

    @Test
    @DisplayName("requireNonNull throws AppIllegalArgumentException when null")
    void requireNonNullThrows() {
        assertThatThrownBy(() -> ValidationUtils.requireNonNull(null, "rating must not be null"))
                .isInstanceOf(AppIllegalArgumentException.class)
                .hasMessage("rating must not be null");
    }

    @Test
    @DisplayName("requirePositive rejects zero and negative values")
    void requirePositiveRejectsNonPositive() {
        assertThatThrownBy(() -> ValidationUtils.requirePositive(0, "K must be positive"))
                .isInstanceOf(AppIllegalArgumentException.class);
        assertThatThrownBy(() -> ValidationUtils.requirePositive(-2.0, "weight must be positive"))
                .isInstanceOf(AppIllegalArgumentException.class);
    }

    @Test
    @DisplayName("requireNonEmpty rejects null and empty collections")
    void requireNonEmptyRejectsNullAndEmpty() {
        assertThatThrownBy(() -> ValidationUtils.requireNonEmpty(null, "userVectors must not be null or empty"))
                .isInstanceOf(AppIllegalArgumentException.class);
        assertThatThrownBy(() -> ValidationUtils.requireNonEmpty(List.of(), "userVectors must not be null or empty"))
                .isInstanceOf(AppIllegalArgumentException.class);
    }
}