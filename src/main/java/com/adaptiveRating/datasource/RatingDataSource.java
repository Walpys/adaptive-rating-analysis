package com.adaptiveRating.datasource;

import com.adaptiveRating.model.Rating;
import com.adaptiveRating.model.UserProfile;

import java.util.List;
import java.util.Map;

public interface RatingDataSource {

    List<Rating> getUserRatings(String userId);

    List<Rating> getAllRatings();

    List<Rating> getObjectRatings(String objectId);

    UserProfile getUserProfile(String userId);

    Map<String, List<Rating>> getUserRatingsBatch(List<String> userIds);
}