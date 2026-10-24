package com.adaptiveRating.api;

import com.adaptiveRating.clustering.KMeans;
import com.adaptiveRating.config.AnalyzerConfig;
import com.adaptiveRating.model.ClusterModel;
import com.adaptiveRating.model.UserVector;
import com.adaptiveRating.validation.ValidationUtils;
import lombok.RequiredArgsConstructor;

import java.util.List;
@RequiredArgsConstructor
public class ClusterAnalysisApi {

    private final AnalyzerConfig config;

    public ClusterModel buildClusters(List<UserVector> userVectors) {
        ValidationUtils.requireNonEmpty(userVectors, "userVectors must not be null or empty");

        KMeans kMeans = new KMeans(
                config.getKMeansClusters(),
                config.getMaxKMeansIterations(),
                config.getKMeansSeed()
        );
        return kMeans.cluster(userVectors);
    }

    public ClusterModel buildClusters(List<UserVector> userVectors, int customK) {
        ValidationUtils.requireNonEmpty(userVectors, "userVectors must not be null or empty");
        ValidationUtils.requirePositive(customK, "K must be positive");

        KMeans kMeans = new KMeans(
                customK,
                config.getMaxKMeansIterations(),
                config.getKMeansSeed()
        );
        return kMeans.cluster(userVectors);
    }
}