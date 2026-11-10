package io.github.walpys.adaptiverating.eval;

import io.github.walpys.adaptiverating.api.AdaptiveRatingAnalyzer;
import io.github.walpys.adaptiverating.config.AnalyzerConfig;
import io.github.walpys.adaptiverating.datasource.InMemoryRatingDataSource;
import io.github.walpys.adaptiverating.model.ClusterModel;
import io.github.walpys.adaptiverating.model.ProfileAnalysisResult;
import io.github.walpys.adaptiverating.model.Rating;
import io.github.walpys.adaptiverating.model.UserVector;
import io.github.walpys.adaptiverating.model.enums.ResultClassification;
import io.github.walpys.adaptiverating.profile.UserProfileBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

public class EvaluationTest {

    public enum GroundTruthLabel {
        NORMAL,
        ANOMALOUS,
        BORDERLINE
    }

    public enum ScenarioType {
        BOTS,
        HATERS,
        FANS,
        LEGIT_IMPORTERS,
        HOURLY_BURSTS,
        DAILY_BURSTS,
        OLD_BURSTS,
        CONSTANT,
        POLARIZED,
        RANDOM,
        LOW_SD_EDGE,
        CONTRARIANS,
        REVIEW_BOMBING,
        SPARSE_USERS,
        NORMAL
    }

    public record GroundTruthItem(
            String userId,
            ScenarioType scenario,
            GroundTruthLabel label,
            List<String> expectedSignals,
            List<Rating> ratings
    ) {
    }

    @Test
    @DisplayName("Evaluation Benchmark: Grade accuracy metrics and generate markdown report")
    public void runEvaluationAndGradeReport() throws Exception {
        try (var reader = new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("ground_truth.json"),
                        "ground_truth.json not found in resources!"), StandardCharsets.UTF_8)) {

            Type listType = new TypeToken<List<GroundTruthItem>>() {
            }.getType();
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(Rating.class, (JsonDeserializer<Rating>) (json, typeOfT, context) -> {
                        JsonObject obj = json.getAsJsonObject();
                        String userId = obj.get("userId").getAsString();
                        String objectId = obj.get("objectId").getAsString();
                        double ratingVal = obj.get("rating").getAsDouble();
                        Instant timestamp = Instant.parse(obj.get("timestamp").getAsString());

                        Instant updatedAt = null;
                        if (obj.has("updatedAt") && !obj.get("updatedAt").isJsonNull()) {
                            updatedAt = Instant.parse(obj.get("updatedAt").getAsString());
                        }

                        return new Rating(userId, objectId, ratingVal, timestamp, updatedAt);
                    })
                    .create();

            List<GroundTruthItem> dataset = gson.fromJson(reader, listType);
            AnalyzerConfig config = AnalyzerConfig.defaultConfig();

            // 1. Collect all ratings into a single shared dataset
            List<Rating> allRatings = new ArrayList<>();
            Map<String, GroundTruthItem> itemMap = new HashMap<>();
            for (GroundTruthItem item : dataset) {
                allRatings.addAll(item.ratings());
                itemMap.put(item.userId(), item);
            }

            InMemoryRatingDataSource ds = new InMemoryRatingDataSource(allRatings);

            // 2. Initialize the bootstrap analyzer for profile management and clustering
            var profileBuilder = new UserProfileBuilder();
            AdaptiveRatingAnalyzer bootstrapAnalyzer = AdaptiveRatingAnalyzer.builder()
                    .config(config)
                    .dataSource(ds)
                    .profileBuilder(profileBuilder)
                    .clusterCount(config.getKMeansClusters())
                    .build();

            // Filter users with enough ratings for clustering
            List<String> validUsers = dataset.stream()
                    .filter(item -> item.ratings().size() >= config.getMinRatings())
                    .map(GroundTruthItem::userId)
                    .toList();

            // Generate user vectors for clustering
            List<UserVector> vectors = validUsers.stream()
                    .map(u -> bootstrapAnalyzer.buildProfile(u, ds.getUserRatings(u)).getUserVector())
                    .toList();

            // 3. Build cluster model using K-Means
            ClusterModel clusterModel = bootstrapAnalyzer.buildClusters(vectors);

            // Save full profiles with cluster assignments to the shared DataSource
            for (String u : validUsers) {
                ds.saveProfile(bootstrapAnalyzer.buildProfileWithCluster(u, clusterModel));
            }

            // 4. Create the final fully-configured analyzer with the trained cluster model (effectively final)
            AdaptiveRatingAnalyzer analyzer = AdaptiveRatingAnalyzer.builder()
                    .config(config)
                    .dataSource(ds)
                    .profileBuilder(profileBuilder)
                    .clusterModel(clusterModel)
                    .clusterCount(config.getKMeansClusters())
                    .build();

            Map<ScenarioType, int[]> scenarioStats = new EnumMap<>(ScenarioType.class); // [TP, FP, FN, TN]
            Map<ScenarioType, List<String>> missedSignalsLog = new EnumMap<>(ScenarioType.class);

            // 5. Run evaluation for each user
            for (GroundTruthItem item : dataset) {
                scenarioStats.putIfAbsent(item.scenario(), new int[4]);
                int[] stats = scenarioStats.get(item.scenario());

                if (item.ratings().size() < config.getMinRatings()) {
                    stats[3]++; // TN
                    continue;
                }

                ProfileAnalysisResult result = analyzer.analyzeProfile(item.userId(), clusterModel);
                ResultClassification classification = result.classification();

                boolean actualAnomaly = classification != ResultClassification.NORMAL;

                if (item.label() == GroundTruthLabel.BORDERLINE) {
                    if (!actualAnomaly) {
                        stats[3]++; // TN
                    } else {
                        stats[1]++; // FP
                    }
                } else {
                    boolean expectedAnomaly = item.label() == GroundTruthLabel.ANOMALOUS;

                    if (expectedAnomaly && actualAnomaly) {
                        stats[0]++; // TP
                    } else if (!expectedAnomaly && actualAnomaly) {
                        stats[1]++; // FP
                    } else if (expectedAnomaly && !actualAnomaly) {
                        stats[2]++; // FN
                        missedSignalsLog.computeIfAbsent(item.scenario(), k -> new ArrayList<>())
                                .add(String.format("User %s missed. Overall: %.2f, Signals: %s",
                                        item.userId(), result.overallAnomalyScore(), result.signalMap()));
                    } else {
                        stats[3]++; // TN
                    }
                }
            }

            // 6. Generate Markdown report in target directory
            StringBuilder md = new StringBuilder();
            md.append("# Evaluation Report (Benchmark Results)\n\n");
            md.append("| Scenario | Precision | Recall | TP | FP | FN | TN |\n");
            md.append("| :--- | :---: | :---: | :---: | :---: | :---: | :---: |\n");

            for (var entry : scenarioStats.entrySet()) {
                ScenarioType scenario = entry.getKey();
                int[] m = entry.getValue();

                String precisionStr = (m[0] + m[1]) == 0 ? "N/A" : String.format("%.2f", (double) m[0] / (m[0] + m[1]));
                String recallStr = (m[0] + m[2]) == 0 ? "N/A" : String.format("%.2f", (double) m[0] / (m[0] + m[2]));

                md.append(String.format("| %s | %s | %s | %d | %d | %d | %d |\n",
                        scenario.name(), precisionStr, recallStr, m[0], m[1], m[2], m[3]));
            }

            if (!missedSignalsLog.isEmpty()) {
                md.append("\n### Missed Scenarios Analysis (Debug Signals)\n");
                for (var miss : missedSignalsLog.entrySet()) {
                    md.append(String.format("- **%s**:\n", miss.getKey().name()));
                    for (String detail : miss.getValue()) {
                        md.append(String.format("  - %s\n", detail));
                    }
                }
            }

            File reportDir = new File("target");
            if (!reportDir.exists()) reportDir.mkdirs();
            try (FileWriter writer = new FileWriter(new File(reportDir, "evaluation-report.md"), StandardCharsets.UTF_8)) {
                writer.write(md.toString());
            }

            System.out.println("Evaluation report successfully generated at target/evaluation-report.md");

            // 7. Benchmark Assertions
            int[] normalStats = scenarioStats.getOrDefault(ScenarioType.NORMAL, new int[4]);
            int[] botStats = scenarioStats.getOrDefault(ScenarioType.BOTS, new int[4]);
            int[] haterStats = scenarioStats.getOrDefault(ScenarioType.HATERS, new int[4]);

            assertAll("Evaluation Benchmark Assertions",
                    () -> assertThat(normalStats[1])
                            .withFailMessage("NORMAL users should have 0 False Positives, but got %d", normalStats[1])
                            .isZero(),
                    () -> assertThat(botStats[0])
                            .withFailMessage("Expected all BOTS to be detected as anomalous (TP > 0)")
                            .isGreaterThan(0),
                    () -> assertThat(haterStats[0])
                            .withFailMessage("Expected HATERS to be detected as anomalous (TP > 0)")
                            .isGreaterThan(0)
            );
        }
    }
}