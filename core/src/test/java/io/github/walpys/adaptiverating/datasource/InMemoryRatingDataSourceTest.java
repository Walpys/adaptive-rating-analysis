package io.github.walpys.adaptiverating.datasource;

import io.github.walpys.adaptiverating.model.Rating;
import io.github.walpys.adaptiverating.model.UserProfile;
import io.github.walpys.adaptiverating.model.UserVector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryRatingDataSourceTest {

    private Rating rating(String userId, String objectId, double val) {
        return new Rating(userId, objectId, val, Instant.now(), null);
    }

    private UserVector validVector() {
        return new UserVector(new double[]{1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0});
    }

    @Nested
    @DisplayName("Initialization & Indexing Tests")
    class InitTests {

        @Test
        @DisplayName("initializes with null ratings safely without throwing")
        void initNullRatings() {
            InMemoryRatingDataSource ds = new InMemoryRatingDataSource(null);
            assertThat(ds.getAllRatings()).isEmpty();
            assertThat(ds.getAllUserIds()).isEmpty();
            assertThat(ds.getAllObjectIds()).isEmpty();
        }

        @Test
        @DisplayName("indexes initial ratings correctly across users and objects")
        void initAndIndexRatings() {
            List<Rating> initial = List.of(
                    rating("u1", "obj1", 5.0),
                    rating("u1", "obj2", 4.0),
                    rating("u2", "obj1", 3.0)
            );

            InMemoryRatingDataSource ds = new InMemoryRatingDataSource(initial);

            assertThat(ds.getAllUserIds()).containsExactlyInAnyOrder("u1", "u2");
            assertThat(ds.getAllObjectIds()).containsExactlyInAnyOrder("obj1", "obj2");
            assertThat(ds.getAllRatings()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Retrieval & Fetching Methods")
    class RetrievalTests {

        @Test
        @DisplayName("getUserRatings returns immutable list or empty list")
        void getUserRatings() {
            InMemoryRatingDataSource ds = new InMemoryRatingDataSource(List.of(
                    rating("u1", "obj1", 5.0)
            ));

            assertThat(ds.getUserRatings("u1")).hasSize(1);
            assertThat(ds.getUserRatings("non-existent")).isEmpty();
            assertThatThrownBy(() -> ds.getUserRatings(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("getObjectRatings returns ratings by object ID")
        void getObjectRatings() {
            InMemoryRatingDataSource ds = new InMemoryRatingDataSource(List.of(
                    rating("u1", "obj1", 5.0),
                    rating("u2", "obj1", 4.0)
            ));

            assertThat(ds.getObjectRatings("obj1")).hasSize(2);
            assertThat(ds.getObjectRatings("obj-missing")).isEmpty();
            assertThatThrownBy(() -> ds.getObjectRatings(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("getUserRatingsBatch returns map for existing users, omitting missing ones")
        void getUserRatingsBatch() {
            InMemoryRatingDataSource ds = new InMemoryRatingDataSource(List.of(
                    rating("u1", "obj1", 5.0),
                    rating("u2", "obj1", 4.0)
            ));

            Map<String, List<Rating>> batch = ds.getUserRatingsBatch(List.of("u1", "missing-user", "u2"));

            assertThat(batch).containsKeys("u1", "u2");
            assertThat(batch).doesNotContainKey("missing-user");
            assertThat(batch.get("u1")).hasSize(1);

            assertThatThrownBy(() -> ds.getUserRatingsBatch(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Pagination & Streaming Tests")
    class StreamAndPaginationTests {

        @Test
        @DisplayName("streamAllRatings streams all cached items successfully")
        void streamAllRatings() {
            InMemoryRatingDataSource ds = new InMemoryRatingDataSource(List.of(
                    rating("u1", "obj1", 1.0),
                    rating("u2", "obj2", 2.0)
            ));

            try (Stream<Rating> stream = ds.streamAllRatings()) {
                assertThat(stream).hasSize(2);
            }
        }

        @Test
        @DisplayName("getRatingsBatch returns correct sublists with validation")
        void getRatingsBatch() {
            InMemoryRatingDataSource ds = new InMemoryRatingDataSource(List.of(
                    rating("u1", "obj1", 1.0),
                    rating("u1", "obj2", 2.0),
                    rating("u1", "obj3", 3.0)
            ));

            assertThat(ds.getRatingsBatch(0, 2)).hasSize(2);
            assertThat(ds.getRatingsBatch(1, 5)).hasSize(2); // offset 1, limit 5 caps at size 3
            assertThat(ds.getRatingsBatch(5, 2)).isEmpty(); // offset beyond size

            assertThatThrownBy(() -> ds.getRatingsBatch(-1, 2))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ds.getRatingsBatch(0, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ds.getRatingsBatch(0, -5))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Cluster & Object Filtering Tests")
    class ClusterFilterTests {

        @Test
        @DisplayName("getObjectRatingsInCluster filters ratings based on user cluster index")
        void getObjectRatingsInCluster() {
            InMemoryRatingDataSource ds = new InMemoryRatingDataSource(List.of(
                    rating("u1", "obj1", 5.0),
                    rating("u2", "obj1", 4.0),
                    rating("u3", "obj1", 3.0)
            ));


            ds.saveProfile(new UserProfile("u1", 10, Map.of(5, 10), validVector(), 5.0, 5.0, 0.0, 5.0, 5.0, 0));
            ds.saveProfile(new UserProfile("u2", 10, Map.of(4, 10), validVector(), 4.0, 4.0, 0.0, 4.0, 4.0, 1));

            List<Rating> cluster0Ratings = ds.getObjectRatingsInCluster("obj1", 0);
            assertThat(cluster0Ratings).hasSize(1);
            assertThat(cluster0Ratings.get(0).userId()).isEqualTo("u1");

            List<Rating> allClusterRatings = ds.getObjectRatingsInCluster("obj1", null);
            assertThat(allClusterRatings).hasSize(3);

            assertThat(ds.getObjectRatingsInCluster("missing-obj", 0)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Profiles & Dynamic Additions")
    class MutationTests {

        @Test
        @DisplayName("addRating dynamically indexes new ratings and updates snapshot")
        void addRatingDynamic() {
            InMemoryRatingDataSource ds = new InMemoryRatingDataSource(List.of());
            assertThat(ds.getAllRatings()).isEmpty();

            Rating r = rating("u-new", "obj-new", 5.0);
            ds.addRating(r);

            assertThat(ds.getAllRatings()).hasSize(1);
            assertThat(ds.getUserRatings("u-new")).containsExactly(r);
            assertThat(ds.getObjectRatings("obj-new")).containsExactly(r);

            assertThatThrownBy(() -> ds.addRating(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("saveProfile and getUserProfile work correctly")
        void profileManagement() {
            InMemoryRatingDataSource ds = new InMemoryRatingDataSource(List.of());
            UserProfile profile = new UserProfile("u1", 20, Map.of(4, 20), validVector(), 4.5, 4.5, 0.5, 4.0, 5.0, 0);

            ds.saveProfile(profile);

            assertThat(ds.getUserProfile("u1")).isEqualTo(profile);
            assertThat(ds.getUserProfile("missing")).isNull();

            assertThatThrownBy(() -> ds.saveProfile(null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> ds.getUserProfile(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}