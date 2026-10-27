package com.adaptiveRating.clustering;

import com.adaptiveRating.exception.cluster.ClusterValidationException;
import com.adaptiveRating.model.ClusterModel;
import com.adaptiveRating.model.UserVector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.adaptiveRating.TestFixtures.EPS;
import static com.adaptiveRating.TestFixtures.unitAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class KMeansTest {

    @Test
    @DisplayName("well separated vectors form two distinct clusters")
    void wellSeparatedVectorsFormTwoClusters() {
        List<UserVector> vectors = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            vectors.add(unitAt(1));
            vectors.add(unitAt(10));
        }

        ClusterModel model = new KMeans(2, 50, 42L).cluster(vectors);

        assertThat(model.getK()).isEqualTo(2);
        assertThat(model.getClusterCenters()).hasSize(2);
        assertThat(model.getClusterCenters()).anyMatch(center -> closeTo(center, unitAt(1)));
        assertThat(model.getClusterCenters()).anyMatch(center -> closeTo(center, unitAt(10)));
    }

    @Test
    @DisplayName("identical vectors share the same center")
    void identicalVectorsShareTheSameCenter() {
        UserVector same = unitAt(4);
        List<UserVector> vectors = List.of(same, same, same, same);

        ClusterModel model = new KMeans(3, 20, 7L).cluster(vectors);

        assertThat(model.getClusterCenters()).isNotEmpty();
        for (UserVector center : model.getClusterCenters()) {
            assertThat(center.components()).containsExactly(same.components());
        }
    }

    @Test
    @DisplayName("k equals one produces mean center of all vectors")
    void kEqualsOneProducesMeanCenter() {
        List<UserVector> vectors = List.of(unitAt(1), unitAt(10));

        ClusterModel model = new KMeans(1, 30, 1L).cluster(vectors);

        assertThat(model.getK()).isEqualTo(1);
        assertThat(model.getClusterCenters()).hasSize(1);
        double[] center = model.getClusterCenters().get(0).components();
        assertThat(center[0]).isCloseTo(0.5, within(EPS));
        assertThat(center[9]).isCloseTo(0.5, within(EPS));
        for (int i = 1; i < 9; i++) {
            assertThat(center[i]).isCloseTo(0.0, within(EPS));
        }
    }

    @Test
    @DisplayName("fixed seed produces reproducible centers")
    void fixedSeedIsReproducible() {
        List<UserVector> vectors = List.of(unitAt(1), unitAt(3), unitAt(8), unitAt(10));
        KMeans first = new KMeans(2, 40, 42L);
        KMeans second = new KMeans(2, 40, 42L);

        ClusterModel a = first.cluster(vectors);
        ClusterModel b = second.cluster(vectors);

        assertThat(a.getK()).isEqualTo(b.getK());
        assertThat(a.getClusterCenters()).hasSameSizeAs(b.getClusterCenters());
        for (int i = 0; i < a.getClusterCenters().size(); i++) {
            assertThat(a.getClusterCenters().get(i).components())
                    .containsExactly(b.getClusterCenters().get(i).components());
        }
    }

    @Test
    @DisplayName("k is capped by vector count when vectors are fewer than k")
    void kIsCappedByVectorCount() {
        List<UserVector> vectors = List.of(unitAt(2), unitAt(9));

        ClusterModel model = new KMeans(5, 10, 3L).cluster(vectors);

        assertThat(model.getK()).isEqualTo(2);
        assertThat(model.getClusterCenters()).hasSize(2);
    }

    @Test
    @DisplayName("null or empty vectors list throws exception")
    void nullOrEmptyVectorsThrowsException() {
        KMeans kMeans = new KMeans(3, 10, 42L);

        assertThatThrownBy(() -> kMeans.cluster(null))
                .isInstanceOf(RuntimeException.class);

        assertThatThrownBy(() -> kMeans.cluster(List.of()))
                .isInstanceOf(ClusterValidationException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    @DisplayName("findClosestCluster returns minus one for invalid inputs")
    void findClosestClusterReturnsMinusOneForInvalidInputs() {
        UserVector vector = unitAt(1);
        ClusterModel validModel = new ClusterModel(1, List.of(vector), 0L);
        ClusterModel emptyModel = new ClusterModel(0, List.of(), 0L);

        assertThat(KMeans.findClosestCluster(null, validModel)).isEqualTo(-1);
        assertThat(KMeans.findClosestCluster(vector, null)).isEqualTo(-1);
        assertThat(KMeans.findClosestCluster(vector, emptyModel)).isEqualTo(-1);
    }

    private static boolean closeTo(UserVector actual, UserVector expected) {
        double[] a = actual.components();
        double[] e = expected.components();
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - e[i]) > 1e-6) {
                return false;
            }
        }
        return true;
    }
}