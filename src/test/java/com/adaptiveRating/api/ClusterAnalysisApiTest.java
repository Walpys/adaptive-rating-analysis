package com.adaptiveRating.api;

import com.adaptiveRating.config.AnalyzerConfig;
import com.adaptiveRating.exception.AppIllegalArgumentException;
import com.adaptiveRating.model.ClusterModel;
import com.adaptiveRating.model.UserVector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.adaptiveRating.TestFixtures.config;
import static com.adaptiveRating.TestFixtures.unitAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClusterAnalysisApiTest {

    private final ClusterAnalysisApi api = new ClusterAnalysisApi(config(3));

    @Test
    @DisplayName("buildClusters будує модель з K із конфігурації")
    void buildClustersUsesConfigK() {
        List<UserVector> vectors = List.of(unitAt(1), unitAt(5), unitAt(10));

        ClusterModel model = api.buildClusters(vectors);

        assertThat(model.getK()).isEqualTo(3);
        assertThat(model.getClusterCenters()).hasSize(3);
        assertThat(model.getTrainingTimestamp()).isPositive();
    }

    @Test
    @DisplayName("buildClusters(customK) використовує передане K")
    void buildClustersWithCustomK() {
        List<UserVector> vectors = List.of(unitAt(1), unitAt(2), unitAt(9), unitAt(10));

        ClusterModel model = api.buildClusters(vectors, 1);

        assertThat(model.getK()).isEqualTo(1);
        assertThat(model.getClusterCenters()).hasSize(1);
    }

    @Test
    @DisplayName("порожній список векторів — fail-fast через ValidationUtils")
    void emptyVectorsFailFast() {
        assertThatThrownBy(() -> api.buildClusters(List.of()))
                .isInstanceOf(AppIllegalArgumentException.class)
                .hasMessageContaining("userVectors");
    }

    @Test
    @DisplayName("null-список векторів — fail-fast")
    void nullVectorsFailFast() {
        assertThatThrownBy(() -> api.buildClusters(null))
                .isInstanceOf(AppIllegalArgumentException.class)
                .hasMessageContaining("userVectors");
    }

    @Test
    @DisplayName("негативне customK — fail-fast requirePositive")
    void nonPositiveCustomKFailFast() {
        List<UserVector> vectors = List.of(unitAt(1), unitAt(10));

        assertThatThrownBy(() -> api.buildClusters(vectors, 0))
                .isInstanceOf(AppIllegalArgumentException.class)
                .hasMessageContaining("K must be positive");
    }

    @Test
    @DisplayName("той самий seed у конфігу дає відтворювані кластери")
    void configSeedIsReproducible() {
        AnalyzerConfig cfg = config(3);
        List<UserVector> vectors = List.of(unitAt(1), unitAt(4), unitAt(8), unitAt(10));

        ClusterModel first = new ClusterAnalysisApi(cfg).buildClusters(vectors, 2);
        ClusterModel second = new ClusterAnalysisApi(cfg).buildClusters(vectors, 2);

        assertThat(first.getK()).isEqualTo(second.getK());
        for (int i = 0; i < first.getClusterCenters().size(); i++) {
            assertThat(first.getClusterCenters().get(i).components())
                    .containsExactly(second.getClusterCenters().get(i).components());
        }
    }
}
