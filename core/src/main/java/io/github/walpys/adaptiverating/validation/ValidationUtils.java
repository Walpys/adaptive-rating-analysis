package io.github.walpys.adaptiverating.validation;

import io.github.walpys.adaptiverating.exception.AppIllegalArgumentException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Collection;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ValidationUtils {

    public static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new AppIllegalArgumentException(message);
        }
        return obj;
    }

    public static void requirePositive(int value, String message) {
        if (value <= 0) {
            throw new AppIllegalArgumentException(message);
        }
    }

    public static void requirePositive(double value, String message) {
        if (value <= 0.0) {
            throw new AppIllegalArgumentException(message);
        }
    }

    public static <T> void requireNonEmpty(Collection<T> collection, String message) {
        requireNonNull(collection, message);
        if (collection.isEmpty()) {
            throw new AppIllegalArgumentException(message);
        }
    }
}