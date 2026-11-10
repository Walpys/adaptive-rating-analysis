package io.github.walpys.adaptiverating.benchmark;



import io.github.walpys.adaptiverating.anomaly.*;
import io.github.walpys.adaptiverating.datasource.RatingDataSource;
import io.github.walpys.adaptiverating.model.ClusterModel;
import io.github.walpys.adaptiverating.model.Rating;
import io.github.walpys.adaptiverating.model.UserProfile;
import io.github.walpys.adaptiverating.model.UserVector;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class AnomalyDetectionBenchmark {

    @Param({"1000", "10000", "50000"})
    private int ratingsCount;

    private AnomalyScoreCalculator scoreCalculator;
    private PersonalAnomalyDetector personalDetector;
    private TemporalAnomalyDetector temporalDetector;
    private ClusterAnomalyDetector clusterDetector;
    private ObjectConsensusAnomalyDetector objectConsensusDetector;

    private Rating singleRating;
    private UserProfile singleProfile;
    private ClusterModel clusterModel;

    private List<Rating> userHistory;
    private List<Rating> globalObjectRatings;

    private RatingDataSource mockDataSource;

    @Setup(Level.Trial)
    public void setup() {

        scoreCalculator = new AnomalyScoreCalculator(
                0.3, 0.2, 0.25, 0.25
        );

        personalDetector = new PersonalAnomalyDetector();
        temporalDetector = TemporalAnomalyDetector.withDefaults();
        clusterDetector = new ClusterAnomalyDetector();
        objectConsensusDetector = new ObjectConsensusAnomalyDetector();

        // 1. Single rating and user profile

        singleRating = new Rating(
                "user_1",
                "obj_1",
                8.5,
                Instant.now()
        );

        UserVector userVector = new UserVector(
                new double[]{
                        0.1, 0.1, 0.1, 0.1, 0.1,
                        0.1, 0.1, 0.1, 0.1, 0.1
                }
        );

        Map<Integer, Integer> ratingCounts = Map.of(
                8, 5,
                9, 10
        );

        singleProfile = new UserProfile(
                "user_1",
                15,
                ratingCounts,
                userVector,
                7.0,
                7.0,
                1.2,
                1.0,
                10.0,
                0
        );

        // 2. Cluster model

        UserVector center1 = new UserVector(
                new double[]{
                        0.1, 0.1, 0.1, 0.1, 0.1,
                        0.1, 0.1, 0.1, 0.1, 0.1
                }
        );

        UserVector center2 = new UserVector(
                new double[]{
                        0.1, 0.1, 0.1, 0.1, 0.1,
                        0.1, 0.1, 0.1, 0.1, 0.1
                }
        );

        clusterModel = new ClusterModel(
                2,
                List.of(center1, center2),
                List.of(60, 40),
                System.currentTimeMillis(),
                List.of(0.75, 0.85)
        );

        // 3. User rating history

        userHistory = new ArrayList<>(ratingsCount);

        Instant baseTime = Instant.now().minus(Duration.ofDays(30));

        for (int i = 0; i < ratingsCount; i++) {

            Instant ratingTime = baseTime.plus(
                    Duration.ofSeconds(i * 10L)
            );

            userHistory.add(
                    new Rating(
                            "user_1",
                            "obj_" + (i % 50),
                            5.0 + (i % 5),
                            ratingTime
                    )
            );
        }

        // 4. Global object ratings

        globalObjectRatings = new ArrayList<>(ratingsCount);

        Instant now = Instant.now();

        for (int i = 0; i < ratingsCount; i++) {

            globalObjectRatings.add(
                    new Rating(
                            "user_" + i,
                            "obj_1",
                            6.0 + (i % 4),
                            now
                    )
            );
        }

        // 5. Mock DataSource

        mockDataSource = createMockDataSource(ratingsCount);
    }

    @Benchmark
    public double benchmarkScoreCalculator() {

        return scoreCalculator.calculateScore(
                0.8, 0.4, 0.2, 0.6
        );
    }

    @Benchmark
    public double benchmarkPersonalAnomaly() {

        return personalDetector.calculateAnomaly(
                singleRating,
                singleProfile
        );
    }

    @Benchmark
    public double benchmarkClusterAnomaly() {

        return clusterDetector.calculateAnomaly(
                singleProfile,
                clusterModel
        );
    }

    @Benchmark
    public double benchmarkTemporalAnomalyProfile() {

        return temporalDetector.calculateProfileAnomaly(
                userHistory
        );
    }

    @Benchmark
    public double benchmarkObjectConsensusProfile() {

        return objectConsensusDetector.calculateProfileAnomaly(
                userHistory,
                objId -> globalObjectRatings,
                objId -> globalObjectRatings
        );
    }

    @Benchmark
    public ConsensusIndex benchmarkConsensusIndexBuild() {

        return ConsensusIndex.build(mockDataSource, 2);
    }

    @Benchmark
    public double benchmarkFullProfilePipeline() {

        double personal = personalDetector.calculateAnomaly(
                singleRating,
                singleProfile
        );

        double cluster = clusterDetector.calculateAnomaly(
                singleProfile,
                clusterModel
        );

        double temporal = temporalDetector.calculateProfileAnomaly(
                userHistory
        );

        double consensus = objectConsensusDetector.calculateProfileAnomaly(
                userHistory,
                objId -> globalObjectRatings,
                objId -> globalObjectRatings
        );

        return scoreCalculator.calculateProfileScore(
                personal,
                cluster,
                temporal,
                consensus
        );
    }

    /**
     * Creates an in-memory mock implementation of RatingDataSource.
     */
    private RatingDataSource createMockDataSource(int size) {

        Map<String, List<Rating>> objectRatings = new HashMap<>();

        Map<String, List<Rating>> userRatings = new HashMap<>();

        List<Rating> allRatings = new ArrayList<>(size);

        Set<String> allUserIds = new HashSet<>();

        Instant now = Instant.now();

        for (int i = 0; i < size; i++) {

            String userId = "user_" + (i % 100);

            Rating rating = new Rating(
                    userId,
                    "obj_" + (i % 10),
                    7.0 + (i % 3),
                    now
            );

            allUserIds.add(userId);

            allRatings.add(rating);

            objectRatings.computeIfAbsent(
                    rating.objectId(),
                    k -> new ArrayList<>()
            ).add(rating);

            userRatings.computeIfAbsent(
                    userId,
                    k -> new ArrayList<>()
            ).add(rating);
        }

        // Immutable snapshots for read-only benchmark operations.

        List<Rating> ratingsSnapshot = List.copyOf(allRatings);

        Map<String, List<Rating>> objectSnapshot = new HashMap<>();

        objectRatings.forEach(
                (key, value) -> objectSnapshot.put(
                        key,
                        List.copyOf(value)
                )
        );

        Map<String, List<Rating>> userSnapshot = new HashMap<>();

        userRatings.forEach(
                (key, value) -> userSnapshot.put(
                        key,
                        List.copyOf(value)
                )
        );

        Set<String> userIdsSnapshot = Set.copyOf(allUserIds);

        Set<String> objectIdsSnapshot = Set.copyOf(
                objectSnapshot.keySet()
        );

        return new RatingDataSource() {

            @Override
            public List<Rating> getObjectRatingsInCluster(
                    String objectId,
                    Integer clusterIndex
            ) {

                return getObjectRatings(objectId);
            }

            @Override
            public List<Rating> getUserRatings(String userId) {

                return userSnapshot.getOrDefault(
                        userId,
                        List.of()
                );
            }

            @Override
            public Set<String> getAllUserIds() {

                return userIdsSnapshot;
            }

            @Override
            public List<Rating> getAllRatings() {

                return ratingsSnapshot;
            }

            @Override
            public Stream<Rating> streamAllRatings() {

                return ratingsSnapshot.stream();
            }

            @Override
            public List<Rating> getRatingsBatch(
                    int offset,
                    int limit
            ) {

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

                if (offset >= ratingsSnapshot.size()) {
                    return List.of();
                }

                int toIndex = (int) Math.min(
                        (long) offset + limit,
                        ratingsSnapshot.size()
                );

                return ratingsSnapshot.subList(offset, toIndex);
            }

            @Override
            public List<Rating> getObjectRatings(String objectId) {

                return objectSnapshot.getOrDefault(
                        objectId,
                        List.of()
                );
            }

            @Override
            public Set<String> getAllObjectIds() {

                return objectIdsSnapshot;
            }

            @Override
            public UserProfile getUserProfile(String userId) {

                return singleProfile;
            }

            @Override
            public Map<String, List<Rating>> getUserRatingsBatch(
                    List<String> userIds
            ) {

                Objects.requireNonNull(
                        userIds,
                        "userIds must not be null"
                );

                Map<String, List<Rating>> result = new LinkedHashMap<>();

                for (String userId : userIds) {

                    List<Rating> ratings = userSnapshot.get(userId);

                    if (ratings != null) {
                        result.put(userId, ratings);
                    }
                }

                return Collections.unmodifiableMap(result);
            }
        };
    }

}
