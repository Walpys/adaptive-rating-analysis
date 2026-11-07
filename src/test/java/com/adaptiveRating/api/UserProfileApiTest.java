package com.adaptiveRating.api;

import com.adaptiveRating.config.AnalyzerConfig;
import com.adaptiveRating.datasource.InMemoryRatingDataSource;
import com.adaptiveRating.exception.AppIllegalArgumentException;
import com.adaptiveRating.exception.rating.RatingValidationException;
import com.adaptiveRating.model.ClusterModel;
import com.adaptiveRating.model.Rating;
import com.adaptiveRating.model.UserProfile;
import com.adaptiveRating.model.UserVector;
import com.adaptiveRating.profile.UserProfileBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.adaptiveRating.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfileApiTest {

    private final AnalyzerConfig config = config(3);
    private final UserProfileBuilder profileBuilder = new UserProfileBuilder();

    @Nested
    @DisplayName("buildProfile methods")
    class BuildProfileTests {

        @Test
        @DisplayName("buildProfile by userId fetches ratings from datasource and builds profile")
        void buildProfileByUserIdSuccess() {
            List<Rating> ratings = history("u1", 5, 6, 7, 8);
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(ratings);
            UserProfileApi api = new UserProfileApi(config, dataSource, profileBuilder);

            UserProfile profile = api.buildProfile("u1");

            assertThat(profile).isNotNull();
            assertThat(profile.getUserId()).isEqualTo("u1");
            assertThat(profile.getTotalRatings()).isEqualTo(4);
        }

        @Test
        @DisplayName("buildProfile with null userId throws exception")
        void buildProfileNullUserIdThrowsException() {
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(List.of());
            UserProfileApi api = new UserProfileApi(config, dataSource, profileBuilder);

            assertThatThrownBy(() -> api.buildProfile(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("userId must not be null");
        }

        @Test
        @DisplayName("buildProfile with insufficient ratings throws RatingValidationException")
        void buildProfileInsufficientRatingsThrowsException() {
            List<Rating> ratings = history("u1", 5, 5);
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(ratings);
            UserProfileApi api = new UserProfileApi(config, dataSource, profileBuilder);

            assertThatThrownBy(() -> api.buildProfile("u1"))
                    .isInstanceOf(RatingValidationException.class);
        }

        @Test
        @DisplayName("buildProfile with explicit rating list builds profile correctly")
        void buildProfileWithRatingListSuccess() {
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(List.of());
            UserProfileApi api = new UserProfileApi(config, dataSource, profileBuilder);

            List<Rating> ratings = history("u1", 8, 9, 10, 7);
            UserProfile profile = api.buildProfile("u1", ratings);

            assertThat(profile).isNotNull();
            assertThat(profile.getTotalRatings()).isEqualTo(4);
            assertThat(profile.getMean()).isGreaterThan(8.0);
        }
    }

    @Nested
    @DisplayName("buildProfilesBatch method")
    class BuildProfilesBatchTests {

        @Test
        @DisplayName("buildProfilesBatch processes multiple valid users successfully")
        void buildProfilesBatchSuccess() {
            List<Rating> allRatings = List.of(
                    rating("u1", "obj-1", 5.0),
                    rating("u1", "obj-2", 5.0),
                    rating("u1", "obj-3", 5.0),
                    rating("u2", "obj-1", 8.0),
                    rating("u2", "obj-2", 8.0),
                    rating("u2", "obj-3", 8.0)
            );
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(allRatings);
            UserProfileApi api = new UserProfileApi(config, dataSource, profileBuilder);

            Map<String, UserProfile> profiles = api.buildProfilesBatch(List.of("u1", "u2"));

            assertThat(profiles).hasSize(2);
            assertThat(profiles).containsKey("u1");
            assertThat(profiles).containsKey("u2");
        }

        @Test
        @DisplayName("buildProfilesBatch skips users with insufficient ratings")
        void buildProfilesBatchSkipsInsufficientUsers() {
            List<Rating> allRatings = List.of(
                    rating("u1", "obj-1", 5.0),
                    rating("u1", "obj-2", 5.0),
                    rating("u1", "obj-3", 5.0),
                    rating("u2", "obj-1", 8.0)
            );
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(allRatings);
            UserProfileApi api = new UserProfileApi(config, dataSource, profileBuilder);

            Map<String, UserProfile> profiles = api.buildProfilesBatch(List.of("u1", "u2"));

            assertThat(profiles).hasSize(1);
            assertThat(profiles).containsKey("u1");
            assertThat(profiles).doesNotContainKey("u2");
        }

        @Test
        @DisplayName("buildProfilesBatch with empty list throws exception")
        void buildProfilesBatchEmptyListThrowsException() {
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(List.of());
            UserProfileApi api = new UserProfileApi(config, dataSource, profileBuilder);

            assertThatThrownBy(() -> api.buildProfilesBatch(List.of()))
                    .isInstanceOf(AppIllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("cluster assignment methods")
    class ClusterAssignmentTests {

        @Test
        @DisplayName("buildProfileWithCluster assigns closest cluster index")
        void buildProfileWithClusterSuccess() {
            List<Rating> ratings = history("u1", 5, 5, 5, 5);
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(ratings);
            UserProfileApi api = new UserProfileApi(config, dataSource, profileBuilder);

            UserVector center = unitAt(5);
            ClusterModel clusterModel = new ClusterModel(1, List.of(center), List.of(0), 1L, List.of(5.0));

            UserProfile profile = api.buildProfileWithCluster("u1", clusterModel);

            assertThat(profile.getClusterIndex()).isNotNull();
            assertThat(profile.getClusterIndex()).isEqualTo(0);
        }

        @Test
        @DisplayName("updateClusterAssignment updates profile cluster index")
        void updateClusterAssignmentSuccess() {
            List<Rating> ratings = history("u1", 5, 5, 5, 5);
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(ratings);
            UserProfileApi api = new UserProfileApi(config, dataSource, profileBuilder);

            UserProfile profile = profileBuilder.build("u1", ratings);
            UserVector center = unitAt(5);
            ClusterModel clusterModel = new ClusterModel(1, List.of(center), List.of(0), 1L, List.of(5.0));

            UserProfile updated = api.updateClusterAssignment(profile, clusterModel);

            assertThat(updated.getClusterIndex()).isEqualTo(0);
        }

        @Test
        @DisplayName("updateClustersBatch updates multiple profiles cluster indices")
        void updateClustersBatchSuccess() {
            List<Rating> ratings1 = history("u1", 5, 5, 5, 5);
            List<Rating> ratings2 = history("u2", 1, 1, 1, 1);
            InMemoryRatingDataSource dataSource = new InMemoryRatingDataSource(List.of());
            UserProfileApi api = new UserProfileApi(config, dataSource, profileBuilder);

            UserProfile p1 = profileBuilder.build("u1", ratings1);
            UserProfile p2 = profileBuilder.build("u2", ratings2);

            UserVector center = unitAt(5);
            ClusterModel clusterModel = new ClusterModel(1, List.of(center), List.of(0), 1L, List.of(5.0));

            api.updateClustersBatch(List.of(p1, p2), clusterModel);

            assertThat(p1.getClusterIndex()).isEqualTo(0);
            assertThat(p2.getClusterIndex()).isEqualTo(0);
        }
    }
}