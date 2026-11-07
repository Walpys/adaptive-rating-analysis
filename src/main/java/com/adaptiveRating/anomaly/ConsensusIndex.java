package com.adaptiveRating.anomaly;

import com.adaptiveRating.datasource.RatingDataSource;
import com.adaptiveRating.model.Rating;
import com.adaptiveRating.model.UserProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConsensusIndex {
    private static final int MIN_OTHER = 5;
    private static final double MIN_STD = 1.0;

    private final Map<String, double[]> byObject;

    private ConsensusIndex(Map<String, double[]> byObject) {
        this.byObject = byObject;
    }

    public static ConsensusIndex build(RatingDataSource ds, int k) {
        Map<String, double[]> map = new HashMap<>(ds.getAllObjectIds().size() * 2);
        for (String objectId : ds.getAllObjectIds()) {
            double[] a = new double[3 * (k + 1)];
            for (Rating r : ds.getObjectRatings(objectId)) {
                double x = r.rating();
                add(a, 0, x);
                UserProfile p = ds.getUserProfile(r.userId());
                if (p != null && p.getClusterIndex() != null) add(a, p.getClusterIndex() + 1, x);
            }
            map.put(objectId, a);
        }
        return new ConsensusIndex(map);
    }

    private static void add(double[] a, int slot, double x) {
        int i = slot * 3;
        a[i] += x;
        a[i + 1] += x * x;
        a[i + 2] += 1;
    }

    public double score(Rating r, Integer clusterIndex) {
        double[] a = byObject.get(r.objectId());
        if (a == null) return Double.NaN;
        double x = r.rating();
        double s = clusterIndex == null ? Double.NaN : fromSlot(a, clusterIndex + 1, x);
        return Double.isNaN(s) ? fromSlot(a, 0, x) : s;
    }

    private static double fromSlot(double[] a, int slot, double x) {
        int i = slot * 3;
        double n = a[i + 2] - 1;
        if (n < MIN_OTHER) return Double.NaN;
        double sum = a[i] - x, sumSq = a[i + 1] - x * x;
        double mean = sum / n;
        double var = Math.max(0.0, (sumSq - sum * sum / n) / (n - 1));
        double z = Math.abs(x - mean) / Math.max(Math.sqrt(var), MIN_STD);
        return Math.min(1.0, Math.max(0.0, (z - 1.0) / 2.0));
    }

    public double profileConsensus(List<Rating> history, Integer clusterIndex) {
        int evaluated = 0, strong = 0;
        for (Rating r : history) {
            double s = score(r, clusterIndex);
            if (Double.isNaN(s)) continue;
            evaluated++;
            if (s >= 0.5) strong++;          // z ≥ 2
        }
        return evaluated == 0 ? 0.0 : (double) strong / evaluated;
    }
}