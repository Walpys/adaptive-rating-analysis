
package io.github.walpys.adaptiverating.anomaly;

import io.github.walpys.adaptiverating.datasource.RatingDataSource;
import io.github.walpys.adaptiverating.model.Rating;
import io.github.walpys.adaptiverating.model.UserProfile;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class ConsensusIndex {

    private static final int MIN_OTHER = 5;
    private static final double MIN_STD = 1.0;

    private final Map<String, double[]> byObject;

    private ConsensusIndex(Map<String, double[]> byObject) {
        this.byObject = Map.copyOf(byObject);
    }

    public static ConsensusIndex build(
            RatingDataSource ds,
            int k,
            Predicate<String> trusted
    ) {
        Objects.requireNonNull(ds, "RatingDataSource must not be null");

        if (k < 0) {
            throw new IllegalArgumentException("k must not be negative");
        }

        Map<String, double[]> map = new HashMap<>();

        Map<String, UserProfile> profileCache = new HashMap<>();

        try (Stream<Rating> ratings = ds.streamAllRatings()) {
            ratings.forEach(r -> {
                if (trusted != null && !trusted.test(r.userId())) {
                    return;
                }

                double[] stats = map.computeIfAbsent(
                        r.objectId(),
                        id -> new double[3 * (k + 1)]
                );

                double x = r.rating();

                add(stats, 0, x);

                UserProfile profile = profileCache.computeIfAbsent(
                        r.userId(),
                        ds::getUserProfile
                );

                if (profile != null && profile.getClusterIndex() != null) {
                    int clusterIndex = profile.getClusterIndex();

                    if (clusterIndex >= 0 && clusterIndex < k) {
                        add(stats, clusterIndex + 1, x);
                    }
                }
            });
        }

        return new ConsensusIndex(map);
    }

    public static ConsensusIndex build(RatingDataSource ds, int k) {
        return build(ds, k, u -> true);
    }

    private static void add(double[] a, int slot, double x) {
        int i = slot * 3;

        a[i] += x;
        a[i + 1] += x * x;
        a[i + 2] += 1;
    }

    public double score(
            Rating r,
            Integer clusterIndex,
            boolean selfIncluded
    ) {
        double[] a = byObject.get(r.objectId());

        if (a == null) {
            return Double.NaN;
        }

        double x = r.rating();

        double s = clusterIndex == null
                ? Double.NaN
                : fromSlot(a, clusterIndex + 1, x, selfIncluded);

        return Double.isNaN(s)
                ? fromSlot(a, 0, x, selfIncluded)
                : s;
    }

    private static double fromSlot(
            double[] a,
            int slot,
            double x,
            boolean selfIncluded
    ) {
        int i = slot * 3;

        if (i + 2 >= a.length) {
            return Double.NaN;
        }

        double n = a[i + 2] - (selfIncluded ? 1 : 0);

        if (n < MIN_OTHER) {
            return Double.NaN;
        }

        double sum = a[i] - (selfIncluded ? x : 0.0);
        double sumSq = a[i + 1] - (selfIncluded ? x * x : 0.0);

        double mean = sum / n;

        double var = Math.max(
                0.0,
                (sumSq - (sum * sum) / n) / (n - 1)
        );

        double z = Math.abs(x - mean)
                / Math.max(Math.sqrt(var), MIN_STD);

        return Math.min(
                1.0,
                Math.max(0.0, (z - 1.0) / 2.0)
        );
    }

    public double profileConsensus(
            java.util.List<Rating> history,
            Integer clusterIndex,
            boolean selfIncluded
    ) {
        int evaluated = 0;
        int strong = 0;

        for (Rating r : history) {
            double s = score(r, clusterIndex, selfIncluded);

            if (Double.isNaN(s)) {
                continue;
            }

            evaluated++;

            if (s >= 0.5) {
                strong++;
            }
        }

        return evaluated == 0
                ? 0.0
                : (double) strong / evaluated;
    }
}
