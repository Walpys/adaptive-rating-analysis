package com.adaptiveRating.api;

import com.adaptiveRating.clustering.KMeans;
import com.adaptiveRating.config.AnalyzerConfig;
import com.adaptiveRating.datasource.RatingDataSource;
import com.adaptiveRating.model.ClusterModel;
import com.adaptiveRating.model.Rating;
import com.adaptiveRating.model.UserProfile;
import com.adaptiveRating.profile.UserProfileBuilder;
import com.adaptiveRating.validation.RatingValidator;
import com.adaptiveRating.validation.ValidationUtils;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


@RequiredArgsConstructor
public class UserProfileApi {

    private final AnalyzerConfig config;
    private final RatingDataSource dataSource;
    private final UserProfileBuilder profileBuilder;

    public UserProfile buildProfile(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        List<Rating> userRatings = dataSource.getUserRatings(userId);
        RatingValidator.validateRatingList(userRatings, config.getMinRatings());
        return profileBuilder.build(userId, userRatings);
    }

    public Map<String, UserProfile> buildProfilesBatch(List<String> userIds) {
        ValidationUtils.requireNonEmpty(userIds, "userIds must not be null or empty");

        Map<String, List<Rating>> batchRatings = dataSource.getUserRatingsBatch(userIds);
        Map<String, UserProfile> resultProfiles = new HashMap<>();

        for (String userId : userIds) {
            List<Rating> userRatings = batchRatings.get(userId);

            if (RatingValidator.isValidRatingList(userRatings, config.getMinRatings())) {
                UserProfile profile = profileBuilder.build(userId, userRatings);
                resultProfiles.put(userId, profile);
            }
        }

        return resultProfiles;
    }

    public UserProfile buildProfileWithCluster(String userId, ClusterModel clusterModel) {
        Objects.requireNonNull(clusterModel, "ClusterModel must not be null");

        UserProfile profile = buildProfile(userId);

        int closestClusterIndex = KMeans.findClosestCluster(profile.getUserVector(), clusterModel);
        profile.setClusterIndex(closestClusterIndex);
        return profile;
    }


    public UserProfile buildProfile(String userId, List<Rating> ratings) {
        RatingValidator.validateRatingList(ratings, config.getMinRatings());
        return profileBuilder.build(userId, ratings);
    }


    public UserProfile updateClusterAssignment(UserProfile profile, ClusterModel clusterModel) {
        Objects.requireNonNull(profile, "UserProfile must not be null");
        Objects.requireNonNull(clusterModel, "ClusterModel must not be null");

        int closestClusterIndex = KMeans.findClosestCluster(profile.getUserVector(), clusterModel);
        profile.setClusterIndex(closestClusterIndex);

        return profile;
    }

    public void updateClustersBatch(List<UserProfile> profiles, ClusterModel clusterModel) {
        Objects.requireNonNull(profiles, "Profiles collection must not be null");
        Objects.requireNonNull(clusterModel, "ClusterModel must not be null");

        for (UserProfile profile : profiles) {
            if (profile != null && profile.getUserVector() != null) {
                int closestClusterIndex = KMeans.findClosestCluster(profile.getUserVector(), clusterModel);
                profile.setClusterIndex(closestClusterIndex);
            }
        }
    }
}