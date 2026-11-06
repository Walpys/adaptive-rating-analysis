package com.adaptiveRating.datasource;

import com.adaptiveRating.model.Rating;
import com.adaptiveRating.model.UserProfile;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryRatingDataSource implements RatingDataSource {

    private final List<Rating> ratings;
    private final Map<String, UserProfile> profiles = new HashMap<>();

    // Індекси для миттєвого пошуку O(1)
    private final Map<String, List<Rating>> userRatingsMap;
    private final Map<String, List<Rating>> objectRatingsMap;

    public InMemoryRatingDataSource(List<Rating> initialRatings) {
        this.ratings = initialRatings != null ? new ArrayList<>(initialRatings) : new ArrayList<>();

        // Будуємо індекси один раз при ініціалізації
        this.userRatingsMap = new HashMap<>();
        this.objectRatingsMap = new HashMap<>();

        for (Rating rating : this.ratings) {
            userRatingsMap.computeIfAbsent(rating.userId(), k -> new ArrayList<>()).add(rating);
            objectRatingsMap.computeIfAbsent(rating.objectId(), k -> new ArrayList<>()).add(rating);
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
        return new ArrayList<>(ratings);
    }

    @Override
    public List<Rating> getObjectRatings(String objectId) {
        Objects.requireNonNull(objectId, "objectId must not be null");
        return objectRatingsMap.getOrDefault(objectId, Collections.emptyList());
    }

    @Override
    public Set<String> getAllObjectIds() {
        // Миттєве повернення ключів готового індексу без зайвих проходів
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

        Set<String> userIdSet = new HashSet<>(userIds);

        return ratings.stream()
                .filter(r -> userIdSet.contains(r.userId()))
                .collect(Collectors.groupingBy(Rating::userId));
    }

    public void addRating(Rating rating) {
        Objects.requireNonNull(rating, "rating must not be null");
        ratings.add(rating);
        userRatingsMap.computeIfAbsent(rating.userId(), k -> new ArrayList<>()).add(rating);
        objectRatingsMap.computeIfAbsent(rating.objectId(), k -> new ArrayList<>()).add(rating);
    }

    public void saveProfile(UserProfile profile) {
        Objects.requireNonNull(profile, "profile must not be null");
        profiles.put(profile.getUserId(), profile);
    }
}