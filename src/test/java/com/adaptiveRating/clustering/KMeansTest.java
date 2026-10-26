package com.adaptiveRating.clustering;

import com.adaptiveRating.model.ClusterModel;
import com.adaptiveRating.model.UserVector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.adaptiveRating.TestFixtures.EPS;
import static com.adaptiveRating.TestFixtures.unitAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class KMeansTest {

    @Test
    @DisplayName("очевидно розділені вектори збираються у два різні центри")
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
    @DisplayName("однакові вектори дають центр, тотожний цьому вектору")
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
    @DisplayName("K=1 завжди будує один центр — середнє всіх векторів")
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
    @DisplayName("фіксований seed дає відтворювані центри")
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
    @DisplayName("якщо векторів менше за K, actualK зменшується до кількості векторів")
    void kIsCappedByVectorCount() {
        List<UserVector> vectors = List.of(unitAt(2), unitAt(9));

        ClusterModel model = new KMeans(5, 10, 3L).cluster(vectors);

        assertThat(model.getK()).isEqualTo(2);
        assertThat(model.getClusterCenters()).hasSize(2);
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
