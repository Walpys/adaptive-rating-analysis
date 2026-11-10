package io.github.walpys.adaptiverating.clustering;

import io.github.walpys.adaptiverating.exception.cluster.ClusterValidationException;
import io.github.walpys.adaptiverating.model.ClusterModel;
import io.github.walpys.adaptiverating.model.UserVector;
import io.github.walpys.adaptiverating.validation.ValidationUtils;

import java.util.*;

public class KMeans {

    private final int k;
    private final int maxIterations;
    private final long seed;

    public KMeans(int k, int maxIterations, long seed) {
        this.k = k;
        this.maxIterations = maxIterations;
        this.seed = seed;
    }

    public ClusterModel cluster(List<UserVector> vectors) {
        ValidationUtils.requireNonNull(vectors, "vectors must not be null");
        if (vectors.isEmpty()) {
            throw new ClusterValidationException("vectors must not be empty");
        }

        int actualK = Math.min(k, vectors.size());
        Random random = new Random(seed);

        List<UserVector> centers = initializeCentersKMeansPlusPlus(vectors, actualK, random);
        List<Integer> assignments = new ArrayList<>(Collections.nCopies(vectors.size(), -1));

        List<List<UserVector>> clusters = new ArrayList<>();

        for (int iter = 0; iter < maxIterations; iter++) {
            boolean changed = false;
            clusters.clear();
            for (int i = 0; i < actualK; i++) {
                clusters.add(new ArrayList<>());
            }

            for (int i = 0; i < vectors.size(); i++) {
                UserVector vector = vectors.get(i);
                int nearestCenter = findNearestCenterIndex(vector, centers);

                if (!assignments.get(i).equals(nearestCenter)) {
                    assignments.set(i, nearestCenter);
                    changed = true;
                }
                clusters.get(nearestCenter).add(vector);
            }

            if (!changed && iter > 0) {
                break;
            }

            List<UserVector> newCenters = new ArrayList<>();
            for (int i = 0; i < actualK; i++) {
                List<UserVector> clusterVectors = clusters.get(i);
                if (clusterVectors.isEmpty()) {
                    newCenters.add(centers.get(i));
                } else {
                    newCenters.add(calculateMeanVector(clusterVectors));
                }
            }
            centers = newCenters;
        }

        List<List<UserVector>> finalClusters = new ArrayList<>();
        for (int i = 0; i < actualK; i++) {
            finalClusters.add(new ArrayList<>());
        }
        for (UserVector vector : vectors) {
            int nearestCenter = findNearestCenterIndex(vector, centers);
            finalClusters.get(nearestCenter).add(vector);
        }

        List<Integer> clusterSizes = new ArrayList<>(actualK);
        List<Double> clusterRadii = new ArrayList<>(actualK);

        for (int i = 0; i < actualK; i++) {
            List<UserVector> clusterVectors = finalClusters.get(i);
            clusterSizes.add(clusterVectors.size());
            clusterRadii.add(calculateP95Radius(clusterVectors, centers.get(i)));
        }

        return new ClusterModel(actualK, centers, clusterSizes, System.currentTimeMillis(), clusterRadii);
    }

    public static int findClosestCluster(UserVector userVector, ClusterModel clusterModel) {
        if (userVector == null || clusterModel == null || clusterModel.getClusterCenters().isEmpty()) {
            return -1;
        }
        return findNearestCenterIndexStatic(userVector, clusterModel.getClusterCenters());
    }

    private int findNearestCenterIndex(UserVector vector, List<UserVector> centers) {
        return findNearestCenterIndexStatic(vector, centers);
    }

    private static int findNearestCenterIndexStatic(UserVector vector, List<UserVector> centers) {
        int nearestIndex = 0;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < centers.size(); i++) {
            double distance = calculateEuclideanDistanceStatic(vector, centers.get(i));
            if (distance < minDistance) {
                minDistance = distance;
                nearestIndex = i;
            }
        }
        return nearestIndex;
    }

    private double calculateP95Radius(List<UserVector> clusterVectors, UserVector center) {
        if (clusterVectors == null || clusterVectors.isEmpty()) {
            return 0.5;
        }

        List<Double> distances = new ArrayList<>(clusterVectors.size());
        for (UserVector v : clusterVectors) {
            distances.add(calculateEuclideanDistanceStatic(v, center));
        }

        Collections.sort(distances);

        int p95Index = (int) Math.ceil(distances.size() * 0.95) - 1;
        p95Index = Math.max(0, Math.min(p95Index, distances.size() - 1));

        double radius = distances.get(p95Index);
        return Math.max(radius, 0.1);
    }

    private static double calculateEuclideanDistanceStatic(UserVector v1, UserVector v2) {
        double[] c1 = v1.components();
        double[] c2 = v2.components();
        double sum = 0.0;
        for (int i = 0; i < 10; i++) {
            double diff = c1[i] - c2[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    private List<UserVector> initializeCentersKMeansPlusPlus(List<UserVector> vectors, int actualK, Random random) {
        List<UserVector> centers = new ArrayList<>();

        centers.add(vectors.get(random.nextInt(vectors.size())));

        for (int i = 1; i < actualK; i++) {
            double[] squaredDistances = new double[vectors.size()];
            double totalSquaredDistSum = 0.0;

            for (int j = 0; j < vectors.size(); j++) {
                UserVector v = vectors.get(j);
                double minSqDist = Double.MAX_VALUE;

                for (UserVector center : centers) {
                    double dist = calculateEuclideanDistanceStatic(v, center);
                    minSqDist = Math.min(minSqDist, dist * dist);
                }

                squaredDistances[j] = minSqDist;
                totalSquaredDistSum += minSqDist;
            }

            double target = random.nextDouble() * totalSquaredDistSum;
            double currentSum = 0.0;
            UserVector chosenCenter = vectors.get(vectors.size() - 1); // fallback

            for (int j = 0; j < vectors.size(); j++) {
                currentSum += squaredDistances[j];
                if (currentSum >= target) {
                    chosenCenter = vectors.get(j);
                    break;
                }
            }
            centers.add(chosenCenter);
        }

        return centers;
    }

    private UserVector calculateMeanVector(List<UserVector> clusterVectors) {
        double[] meanComponents = new double[10];
        int size = clusterVectors.size();

        for (UserVector v : clusterVectors) {
            double[] comps = v.components();
            for (int i = 0; i < 10; i++) {
                meanComponents[i] += comps[i];
            }
        }

        for (int i = 0; i < 10; i++) {
            meanComponents[i] /= size;
        }

        normalizeSum(meanComponents);
        return new UserVector(meanComponents);
    }

    private void normalizeSum(double[] components) {
        double sum = 0.0;
        for (double c : components) {
            sum += c;
        }
        if (sum > 0.0 && Math.abs(sum - 1.0) > 1e-6) {
            for (int i = 0; i < components.length; i++) {
                components[i] /= sum;
            }
        }
    }
}