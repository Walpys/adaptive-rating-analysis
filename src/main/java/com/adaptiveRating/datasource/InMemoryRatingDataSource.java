package com.adaptiveRating.datasource;

import com.adaptiveRating.model.Rating;
import com.adaptiveRating.model.UserProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class InMemoryRatingDataSource implements RatingDataSource {

    private final List<Rating> ratings;
    private final Map<String, UserProfile> profiles = new HashMap<>();

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

    @Override
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

    @Override
    public UserProfile getUserProfile(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return profiles.get(userId);
    }

    @Override
    public Map<String, List<Rating>> getUserRatingsBatch(List<String> userIds) {
        Objects.requireNonNull(userIds, "userIds must not be null");

        return ratings.stream()
                .filter(r -> userIds.contains(r.userId()))
                .collect(Collectors.groupingBy(Rating::userId));
    }

    public void addRating(Rating rating) {
        Objects.requireNonNull(rating, "rating must not be null");
        ratings.add(rating);
    }

    public void saveProfile(UserProfile profile) {
        Objects.requireNonNull(profile, "profile must not be null");
        profiles.put(profile.getUserId(), profile);
    }
}