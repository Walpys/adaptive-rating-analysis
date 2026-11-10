
package io.github.walpys.adaptiverating.datasource;

import io.github.walpys.adaptiverating.model.Rating;
import io.github.walpys.adaptiverating.model.UserProfile;

import java.util.*;
import java.util.stream.Stream;

/**
 * In-memory implementation of {@link RatingDataSource}.
 * <p>
 * Uses indexed collections for efficient access to ratings by users
 * and objects, together with a cached immutable snapshot of all ratings.
 * <p>
 * Thread safety:
 * - Concurrent read operations are supported after initialization,
 * provided no modifications are performed.
 * - addRating() and saveProfile() are not thread-safe.
 */
public class InMemoryRatingDataSource implements RatingDataSource {

    private final Map<String, UserProfile> profiles = new HashMap<>();

    private final Map<String, List<Rating>> userRatingsMap = new HashMap<>();

    private final Map<String, List<Rating>> objectRatingsMap = new HashMap<>();

    /**
     * Cached immutable snapshot of all ratings.
     * Rebuilt when a new rating is added.
     */
    private volatile List<Rating> cachedAllRatings = List.of();

    public InMemoryRatingDataSource(List<Rating> initialRatings) {

        if (initialRatings != null) {
            for (Rating rating : initialRatings) {
                indexRating(Objects.requireNonNull(
                        rating, "rating must not be null"
                ));
            }
        }

        rebuildSnapshot();
    }

    private void indexRating(Rating rating) {

        userRatingsMap
                .computeIfAbsent(rating.userId(), k -> new ArrayList<>())
                .add(rating);

        objectRatingsMap
                .computeIfAbsent(rating.objectId(), k -> new ArrayList<>())
                .add(rating);
    }

    private void rebuildSnapshot() {

        List<Rating> allRatings = new ArrayList<>();

        objectRatingsMap.values().forEach(allRatings::addAll);

        cachedAllRatings = List.copyOf(allRatings);
    }

    @Override
    public List<Rating> getObjectRatingsInCluster(
            String objectId,
            Integer clusterIndex
    ) {

        List<Rating> objectRatings = getObjectRatings(objectId);

        if (clusterIndex == null || objectRatings.isEmpty()) {
            return objectRatings;
        }

        List<Rating> result = new ArrayList<>();

        for (Rating rating : objectRatings) {

            UserProfile profile = profiles.get(rating.userId());

            if (profile != null &&
                    Objects.equals(profile.getClusterIndex(), clusterIndex)) {

                result.add(rating);
            }
        }

        return List.copyOf(result);
    }

    @Override
    public List<Rating> getUserRatings(String userId) {

        Objects.requireNonNull(userId, "userId must not be null");

        return List.copyOf(
                userRatingsMap.getOrDefault(userId, Collections.emptyList())
        );
    }

    @Override
    public List<Rating> getAllRatings() {

        return cachedAllRatings;
    }

    @Override
    public Stream<Rating> streamAllRatings() {

        // Uses an immutable snapshot captured when the stream is created.
        return cachedAllRatings.stream();
    }

    @Override
    public List<Rating> getRatingsBatch(int offset, int limit) {

        if (offset < 0) {
            throw new IllegalArgumentException(
                    "offset must not be negative"
            );
        }

        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "limit must be positive"
            );
        }

        List<Rating> snapshot = cachedAllRatings;

        if (offset >= snapshot.size()) {
            return List.of();
        }

        int toIndex = (int) Math.min(
                (long) offset + limit,
                snapshot.size()
        );

        return snapshot.subList(offset, toIndex);
    }

    @Override
    public List<Rating> getObjectRatings(String objectId) {

        Objects.requireNonNull(objectId, "objectId must not be null");

        return List.copyOf(
                objectRatingsMap.getOrDefault(
                        objectId,
                        Collections.emptyList()
                )
        );
    }

    @Override
    public Set<String> getAllObjectIds() {

        return Set.copyOf(objectRatingsMap.keySet());
    }

    @Override
    public UserProfile getUserProfile(String userId) {

        Objects.requireNonNull(userId, "userId must not be null");

        return profiles.get(userId);
    }

    @Override
    public Map<String, List<Rating>> getUserRatingsBatch(
            List<String> userIds
    ) {

        Objects.requireNonNull(userIds, "userIds must not be null");

        Map<String, List<Rating>> result = new LinkedHashMap<>();

        for (String userId : userIds) {

            Objects.requireNonNull(userId, "userId must not be null");

            List<Rating> ratings = userRatingsMap.get(userId);

            if (ratings != null) {
                result.put(userId, List.copyOf(ratings));
            }
        }

        return Collections.unmodifiableMap(result);
    }

    @Override
    public Set<String> getAllUserIds() {

        return Set.copyOf(userRatingsMap.keySet());
    }

    /**
     * Adds a rating and updates all internal indexes and snapshots.
     * <p>
     * This operation is not thread-safe.
     */
    public void addRating(Rating rating) {

        Objects.requireNonNull(rating, "rating must not be null");

        indexRating(rating);

        rebuildSnapshot();
    }

    /**
     * Saves or replaces a user profile.
     * <p>
     * This operation is not thread-safe.
     */
    public void saveProfile(UserProfile profile) {

        Objects.requireNonNull(profile, "profile must not be null");

        profiles.put(profile.getUserId(), profile);
    }
}
