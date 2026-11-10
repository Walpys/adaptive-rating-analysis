package io.github.walpys.adaptiverating.anomaly;

import io.github.walpys.adaptiverating.model.UserProfile;

public class DispersionAnomalyDetector {

    private static final int MIN_RATINGS = 5;
    private static final double MIN_NORMAL_STD_DEV = 0.6;
    private static final double MAX_NORMAL_STD_DEV = 3.0;
    private static final double MAX_POSSIBLE_STD_DEV = 4.55;

    public double calculateAnomaly(UserProfile profile) {
        if (profile.getTotalRatings() < MIN_RATINGS) {
            return 0.0;
        }

        double std = profile.getStandardDeviation();

        if (std < MIN_NORMAL_STD_DEV) {
            return (MIN_NORMAL_STD_DEV - std) / MIN_NORMAL_STD_DEV;
        }
        if (std > MAX_NORMAL_STD_DEV) {
            return Math.min(1.0,
                    (std - MAX_NORMAL_STD_DEV) / (MAX_POSSIBLE_STD_DEV - MAX_NORMAL_STD_DEV));
        }
        return 0.0;
    }
}