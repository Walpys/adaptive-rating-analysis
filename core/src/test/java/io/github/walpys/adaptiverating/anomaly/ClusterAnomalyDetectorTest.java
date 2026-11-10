package io.github.walpys.adaptiverating.anomaly;

import io.github.walpys.adaptiverating.model.ClusterModel;
import io.github.walpys.adaptiverating.model.UserProfile;
import io.github.walpys.adaptiverating.model.UserVector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.github.walpys.adaptiverating.TestFixtures.EPS;
import static io.github.walpys.adaptiverating.TestFixtures.unitAt;
import static org.assertj.core.api.Assertions.*;

class ClusterAnomalyDetectorTest {

    private final ClusterAnomalyDetector detector = new ClusterAnomalyDetector();

    @Test
    @DisplayName("vector at cluster center yields zero anomaly score")
    void vectorAtCenterYieldsZero() {
        UserVector center = unitAt(5);
        ClusterModel model = new ClusterModel(1, List.of(center), List.of(100), 0L, List.of(5.0));
        UserProfile profile = createProfileWithCluster(center, 0);

        double score = detector.calculateAnomaly(profile, model);

        assertThat(score).isCloseTo(0.0, within(EPS));
    }

    @Test
    @DisplayName("orthogonal peaks normalize distance correctly to unit interval")
    void orthogonalPeaksYieldNormalizedScore() {
        UserVector center = unitAt(1);
        UserVector far = unitAt(10);
        ClusterModel model = new ClusterModel(1, List.of(center), List.of(100), 0L, List.of(1.0));
        UserProfile profile = createProfileWithCluster(far, 0);

        double score = detector.calculateAnomaly(profile, model);

        assertThat(score).isGreaterThan(0.0);
        assertThat(score).isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("throws exception when user profile has no cluster index assigned")
    void throwsExceptionWhenClusterIndexIsNull() {
        UserVector vector = unitAt(1);
        ClusterModel model = new ClusterModel(1, List.of(vector), List.of(100), 0L, List.of(1.0));

        UserProfile profile = new UserProfile(
                "user-1", 10, Map.of(), vector, 5.0, 5.0, 1.0, 1.0, 10.0, null
        );

        assertThatThrownBy(() -> detector.calculateAnomaly(profile, model))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("clusterIndex assigned");
    }

    private UserProfile createProfileWithCluster(UserVector vector, int clusterIndex) {
        return new UserProfile(
                "user-1", 10, Map.of(), vector, 5.0, 5.0, 1.0, 1.0, 10.0, clusterIndex
        );
    }
}