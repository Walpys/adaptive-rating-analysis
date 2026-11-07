package com.adaptiveRating.datasource;

import com.adaptiveRating.model.Rating;
import com.adaptiveRating.model.UserProfile;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryRatingDataSource implements RatingDataSource {

    private final Map<String, UserProfile> profiles = new HashMap<>();

    // Індекси для миттєвого пошуку O(1) — вони є єдиним джерелом правди для оцінок
    private final Map<String, List<Rating>> userRatingsMap;
    private final Map<String, List<Rating>> objectRatingsMap;

    public InMemoryRatingDataSource(List<Rating> initialRatings) {
        this.userRatingsMap = new HashMap<>();
        this.objectRatingsMap = new HashMap<>();

        if (initialRatings != null) {
            for (Rating rating : initialRatings) {
                userRatingsMap.computeIfAbsent(rating.userId(), k -> new ArrayList<>()).add(rating);
                objectRatingsMap.computeIfAbsent(rating.objectId(), k -> new ArrayList<>()).add(rating);
            }
        }
    }

    @Override
    public List<Rating> getObjectRatingsInCluster(String objectId, Integer clusterIndex) {
        List<Rating> objectRatings = getObjectRatings(objectId);
        if (clusterIndex == null || objectRatings.isEmpty()) {
            return objectRatings;
        }

        List<Rating> result = new ArrayList<>();
        for (Rating r : objectRatings) {
            UserProfile userProfile = getUserProfile(r.userId());
            if (userProfile != null && Objects.equals(userProfile.getClusterIndex(), clusterIndex)) {
                result.add(r);
            }
        }
        return result;
    }

    @Override
    public List<Rating> getUserRatings(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return userRatingsMap.getOrDefault(userId, Collections.emptyList());
    }

    @Override
    public List<Rating> getAllRatings() {
        // Збираємо всі оцінки з мапи об'єктів без дублюючого окремого списку
        return objectRatingsMap.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    @Override
    public List<Rating> getObjectRatings(String objectId) {
        Objects.requireNonNull(objectId, "objectId must not be null");
        return objectRatingsMap.getOrDefault(objectId, Collections.emptyList());
    }

    @Override
    public Set<String> getAllObjectIds() {
        return objectRatingsMap.keySet();
    }

    @Override
    public UserProfile getUserProfile(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return profiles.get(userId);
    }

    @Override
    public Map<String, List<Rating>> getUserRatingsBatch(List<String> userIds) {
        Objects.requireNonNull(userIds, "userIds must not be null");

        Map<String, List<Rating>> result = new HashMap<>();
        for (String userId : userIds) {
            List<Rating> ratings = userRatingsMap.get(userId);
            if (ratings != null) {
                result.put(userId, ratings);
            }
        }
        return result;
    }

    public void addRating(Rating rating) {
        Objects.requireNonNull(rating, "rating must not be null");
        userRatingsMap.computeIfAbsent(rating.userId(), k -> new ArrayList<>()).add(rating);
        objectRatingsMap.computeIfAbsent(rating.objectId(), k -> new ArrayList<>()).add(rating);
    }

    public void saveProfile(UserProfile profile) {
        Objects.requireNonNull(profile, "profile must not be null");
        profiles.put(profile.getUserId(), profile);
    }
}