package com.adaptiveRating.percentile;

import com.adaptiveRating.model.Rating;

import java.util.List;

public class PercentileCalculator {

    public double calculatePercentile(Rating currentRating, List<Rating> history) {
        double targetValue = currentRating.rating();
        int countLessThan = 0;
        int countEqual = 0;
        int n = history.size();

        for (Rating historicalRating : history) {
            double val = historicalRating.rating();
            if (val < targetValue) {
                countLessThan++;
            } else if (val == targetValue) {
                countEqual++;
            }
        }

        double p = (countLessThan + 0.5 * countEqual) / (double) n;
        return p * 100.0;
    }
}
