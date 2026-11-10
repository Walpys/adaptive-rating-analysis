# RatingDataSource SPI

## 1. Purpose

`RatingDataSource` is the storage abstraction used by `AdaptiveRatingAnalyzer`. The library ships `InMemoryRatingDataSource`; applications can implement the same interface for PostgreSQL, MySQL, another relational database, a document store or a remote service.

The interface is in `io.github.walpys.adaptiverating.datasource` and is exported by `module-info.java`.

## 2. Contract

| Method | Required behavior |
|---|---|
| `getObjectRatingsInCluster(objectId, clusterIndex)` | Return ratings for an object whose users have the requested stored cluster assignment. If `clusterIndex` is `null`, the in-memory implementation returns all ratings for the object. |
| `getUserRatings(userId)` | Return all ratings for one user. Return an empty list when the user has no ratings. |
| `getAllUserIds()` | Return IDs of users who have submitted ratings. |
| `getAllRatings()` | Default implementation materializes `streamAllRatings()` into a list. Avoid this for large datasets. |
| `streamAllRatings()` | Return a stream over all ratings. The caller closes it; database implementations must release cursor/connection resources on stream close. |
| `getRatingsBatch(offset, limit)` | Return a non-null page. `offset >= 0`, `limit > 0`; invalid arguments must throw `IllegalArgumentException`. |
| `getObjectRatings(objectId)` | Return all ratings for an object, or an empty list. |
| `getAllObjectIds()` | Return IDs of objects that have ratings. |
| `getUserProfile(userId)` | Return the stored profile or `null` when it does not exist. |
| `getUserRatingsBatch(userIds)` | Return a map from found user IDs to their ratings. Missing users are omitted. Avoid N+1 database queries. |

Prefer returning immutable snapshots. Do not return `null` for list, set, stream or map results. `getUserProfile` is the exception and explicitly uses `null` for a missing profile.

## 3. Minimal implementation outline

The following is a structural example. Replace `RatingRepository` and `ProfileRepository` with the persistence layer used by your application.

```java
package example.persistence;

import io.github.walpys.adaptiverating.datasource.RatingDataSource;
import io.github.walpys.adaptiverating.model.Rating;
import io.github.walpys.adaptiverating.model.UserProfile;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Stream;

public final class DatabaseRatingDataSource implements RatingDataSource {
    private final RatingRepository ratings;
    private final ProfileRepository profiles;

    public DatabaseRatingDataSource(
            RatingRepository ratings,
            ProfileRepository profiles) {
        this.ratings = Objects.requireNonNull(ratings);
        this.profiles = Objects.requireNonNull(profiles);
    }

    @Override
    public List<Rating> getObjectRatingsInCluster(
            String objectId, Integer clusterIndex) {
        if (clusterIndex == null) {
            return getObjectRatings(objectId);
        }
        return List.copyOf(ratings.findByObjectAndCluster(objectId, clusterIndex));
    }

    @Override
    public List<Rating> getUserRatings(String userId) {
        return List.copyOf(ratings.findByUser(userId));
    }

    @Override
    public Set<String> getAllUserIds() {
        return Set.copyOf(ratings.findDistinctUserIds());
    }

    @Override
    public Stream<Rating> streamAllRatings() {
        // Repository must close its cursor/connection when this stream closes.
        return ratings.streamAll();
    }

    @Override
    public List<Rating> getRatingsBatch(int offset, int limit) {
        if (offset < 0) throw new IllegalArgumentException("offset must not be negative");
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        return List.copyOf(ratings.findPage(offset, limit));
    }

    @Override
    public List<Rating> getObjectRatings(String objectId) {
        return List.copyOf(ratings.findByObject(objectId));
    }

    @Override
    public Set<String> getAllObjectIds() {
        return Set.copyOf(ratings.findDistinctObjectIds());
    }

    @Override
    public UserProfile getUserProfile(String userId) {
        return profiles.findByUserId(userId).orElse(null);
    }

    @Override
    public Map<String, List<Rating>> getUserRatingsBatch(List<String> userIds) {
        Objects.requireNonNull(userIds, "userIds must not be null");
        return ratings.findByUsers(userIds).entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> List.copyOf(e.getValue())
                ));
    }
}
```

The repository types above are illustrative interfaces, not classes included in this project. The implementation must also define how it handles duplicate ratings, ordering and concurrent writes.

## 4. Database query guidance

Suggested relational indexes:

```sql
CREATE INDEX idx_rating_user_time
    ON rating (user_id, timestamp);

CREATE INDEX idx_rating_object_user
    ON rating (object_id, user_id);

CREATE INDEX idx_profile_cluster
    ON user_profile (cluster_index, user_id);
```

Adapt names and column types to the actual schema. If `updated_at` is used to determine effective event time, include it in the selected columns and preserve its nullable semantics.

Suggested query shapes:

- User history: `WHERE user_id = ? ORDER BY timestamp, object_id`.
- Object ratings: `WHERE object_id = ?`.
- Object and cluster: join ratings to the current profile table on `user_id`, then filter by `object_id` and `cluster_index`.
- Distinct user/object IDs: `SELECT DISTINCT ...`.
- Batch user history: `WHERE user_id IN (...)`, grouped by user in application code.
- Page: stable `ORDER BY` is mandatory. Offset pagination is part of the SPI contract; keyset pagination is preferable internally for very large tables if the adapter can preserve the requested semantics.

## 5. Stream lifecycle and consistency

Use try-with-resources at call sites:

```java
try (Stream<Rating> stream = dataSource.streamAllRatings()) {
    stream.forEach(this::process);
}
```

A database-backed stream should own a read-only cursor and close the underlying result set, statement and connection when `close()` is called. Do not return a stream backed by a connection that has already been closed.

For an analysis run, use a consistent database snapshot where practical. The analyzer builds a consensus index from all ratings and profile cluster assignments. If assignments change halfway through that scan, the resulting index can mix model versions. Store a cluster-model version or training timestamp with profiles and ensure the query sees one coherent version.

## 6. Performance and correctness checklist

- [ ] Every collection-returning method returns an empty collection rather than `null` when no rows match.
- [ ] `getUserProfile` returns `null` for a missing profile, as documented.
- [ ] `getUserRatingsBatch` omits missing IDs and performs a bounded number of database queries.
- [ ] `streamAllRatings` is genuinely streamed and closes database resources when closed.
- [ ] `getRatingsBatch` validates `offset` and `limit` and uses deterministic ordering.
- [ ] Cluster filtering uses the same profile/cluster version as the analyzer's `ClusterModel`.
- [ ] Ratings preserve `Instant` precision and nullable `updatedAt` values.
- [ ] Queries use bound parameters, not string concatenation.
- [ ] Read consistency and write visibility are documented.
- [ ] Integration tests cover empty data, missing profiles, batch boundaries, stream closure, duplicate ratings and cluster filtering.

## 7. Existing in-memory implementation

`InMemoryRatingDataSource` is useful as a reference implementation. It builds user and object indexes and caches an immutable full-rating snapshot. Reads are suitable for concurrent use after initialization if no writes occur. Its `addRating` and `saveProfile` methods are explicitly not thread-safe. Each `addRating` rebuilds the full snapshot, so a database adapter is preferable for sustained high-volume writes.
