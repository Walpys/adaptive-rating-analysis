package com.adaptiveRating.datasource;

import com.adaptiveRating.model.Rating;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class InMemoryRatingDataSource implements RatingDataSource {

    private final List<Rating> ratings;

    public InMemoryRatingDataSource(List<Rating> initialRatings) {
        this.ratings = initialRatings != null ? new ArrayList<>(initialRatings) : new ArrayList<>();
    }

    @Override
    public List<Rating> getUserRatings(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        List<Rating> userRatings = new ArrayList<>();
        for (Rating rating : ratings) {
            if (rating.userId().equals(userId)) {
                userRatings.add(rating);
            }
        }
        return userRatings;
    }

    @Override
    public List<Rating> getAllRatings() {
        return new ArrayList<>(ratings);
    }

    public List<Rating> getObjectRatings(String objectId) {
        Objects.requireNonNull(objectId, "objectId must not be null");
        List<Rating> objectRatings = new ArrayList<>();
        for (Rating rating : ratings) {
            if (rating.objectId().equals(objectId)) {
                objectRatings.add(rating);
            }
        }
        return objectRatings;
    }

    public void addRating(Rating rating) {
        Objects.requireNonNull(rating, "rating must not be null");
        ratings.add(rating);
    }
}