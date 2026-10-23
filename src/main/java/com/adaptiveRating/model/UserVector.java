package com.adaptiveRating.model;

import com.adaptiveRating.exception.vector.VectorValidationException;

import java.util.Arrays;

public record UserVector(double[] components) {
    public UserVector {
        if (components == null || components.length != 10) {
            throw new VectorValidationException("UserVector must have exactly 10 components.");
        }
        double sum = 0.0;
        for (double v : components) {
            if (v < 0.0 || v > 1.0) {
                throw new VectorValidationException("Vector components must be between 0 and 1.");
            }
            sum += v;
        }
        if (Math.abs(sum - 1.0) > 1e-4) {
            throw new VectorValidationException("Vector components sum must equal 1.0, got: " + sum);
        }
        components = Arrays.copyOf(components, components.length);
    }

    @Override
    public double[] components() {
        return Arrays.copyOf(components, components.length);
    }
}