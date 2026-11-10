package io.github.walpys.adaptiverating;

import io.github.walpys.adaptiverating.config.WindowRule;
import io.github.walpys.adaptiverating.config.AnalyzerConfig;
import io.github.walpys.adaptiverating.model.Rating;
import io.github.walpys.adaptiverating.model.UserVector;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class TestFixtures {

    public static final Instant T0 = Instant.parse("2026-06-01T12:00:00Z");
    public static final double EPS = 1e-9;

    private TestFixtures() {
    }

    public static Rating rating(String userId, double value) {
        return new Rating(userId, "obj-1", value, T0);
    }

    public static Rating rating(String userId, String objectId, double value) {
        return new Rating(userId, objectId, value, T0);
    }

    public static Rating rating(String userId, String objectId, double value, Instant timestamp) {
        return new Rating(userId, objectId, value, timestamp);
    }

    public static List<Rating> history(String userId, double... values) {
        List<Rating> ratings = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            ratings.add(new Rating(userId, "obj-1", values[i], T0.plusSeconds(i)));
        }
        return ratings;
    }

    public static UserVector unitAt(int ratingBucket1to10) {
        double[] components = new double[10];
        components[ratingBucket1to10 - 1] = 1.0;
        return new UserVector(components);
    }

    public static AnalyzerConfig config(int minRatings) {
        return new AnalyzerConfig(
                minRatings,
                3,
                100,
                42L,
                0.4,
                0.3,
                0.2,
                0.1,
                0.40,
                0.75,
                List.of(new WindowRule(Duration.ofHours(1), 5, 20))
        );
    }
}