
package io.github.walpys.adaptiverating.datasource;

import io.github.walpys.adaptiverating.model.Rating;
import io.github.walpys.adaptiverating.model.UserProfile;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Service Provider Interface (SPI) for accessing ratings and user profiles.
 * <p>
 * Implementations should provide efficient streaming and batch operations
 * appropriate for their underlying storage mechanism.
 */
public interface RatingDataSource {

    /**
     * Returns ratings for a specific object and user cluster.
     */
    List<Rating> getObjectRatingsInCluster(String objectId, Integer clusterIndex);

    /**
     * Returns all ratings submitted by a specific user.
     */
    List<Rating> getUserRatings(String userId);

    /**
     * Returns IDs of all users who have submitted ratings.
     */
    Set<String> getAllUserIds();

    /**
     * Returns all ratings as a list.
     * <p>
     * This operation may consume significant memory for large datasets.
     * Prefer {@link #streamAllRatings()} or {@link #getRatingsBatch(int, int)}.
     */
    default List<Rating> getAllRatings() {
        try (Stream<Rating> stream = streamAllRatings()) {
            return stream.toList();
        }
    }

    /**
     * Streams all ratings without requiring the entire dataset
     * to be loaded into memory.
     * <p>
     * Implementations backed by databases should use native streaming
     * mechanisms where available.
     * <p>
     * The caller is responsible for closing the returned stream.
     * This is particularly important for database-backed implementations.
     */
    Stream<Rating> streamAllRatings();

    /**
     * Retrieves a batch of ratings using offset-based pagination.
     * <p>
     * Implementations should perform pagination at the storage level
     * whenever possible.
     *
     * @param offset number of records to skip, starting from zero
     * @param limit  maximum number of records to return
     * @return a batch of ratings, never null
     * @throws IllegalArgumentException if offset is negative or limit is not positive
     */
    List<Rating> getRatingsBatch(int offset, int limit);

    /**
     * Returns ratings associated with a specific object.
     */
    List<Rating> getObjectRatings(String objectId);

    /**
     * Returns IDs of all objects that have received ratings.
     */
    Set<String> getAllObjectIds();

    /**
     * Returns a user's profile or null if the profile does not exist.
     */
    UserProfile getUserProfile(String userId);

    /**
     * Retrieves ratings for multiple users in a single operation.
     * <p>
     * Missing users are omitted from the resulting map.
     * Implementations backed by databases should avoid N+1 queries.
     *
     * @param userIds IDs of users to retrieve
     * @return map containing user IDs and their ratings
     */
    Map<String, List<Rating>> getUserRatingsBatch(List<String> userIds);
}
