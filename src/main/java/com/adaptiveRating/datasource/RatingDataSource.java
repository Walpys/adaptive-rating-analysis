package com.adaptiveRating.datasource;

import com.adaptiveRating.model.Rating;

import java.util.List;

public interface RatingDataSource {

    List<Rating> getUserRatings(String userId);

    List<Rating> getAllRatings();

    List<Rating> getObjectRatings(String objectId);
}