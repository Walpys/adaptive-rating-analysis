package com.adaptiveRating.clustering;

import com.adaptiveRating.model.ClusterModel;
import com.adaptiveRating.model.UserVector;

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
        int actualK = Math.min(k, vectors.size());
        Random random = new Random(seed);

        List<UserVector> centers = initializeCenters(vectors, actualK, random);
        List<Integer> assignments = new ArrayList<>(Collections.nCopies(vectors.size(), -1));

        for (int iter = 0; iter < maxIterations; iter++) {
            boolean changed = false;
            List<List<UserVector>> clusters = new ArrayList<>();
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

        return new ClusterModel(actualK, centers, System.currentTimeMillis());
    }

    private List<UserVector> initializeCenters(List<UserVector> vectors, int actualK, Random random) {
        List<UserVector> centers = new ArrayList<>();
        List<UserVector> shuffled = new ArrayList<>(vectors);
        Collections.shuffle(shuffled, random);

        for (int i = 0; i < actualK; i++) {
            centers.add(shuffled.get(i));
        }
        return centers;
    }

    private int findNearestCenterIndex(UserVector vector, List<UserVector> centers) {
        int nearestIndex = 0;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < centers.size(); i++) {
            double distance = calculateEuclideanDistance(vector, centers.get(i));
            if (distance < minDistance) {
                minDistance = distance;
                nearestIndex = i;
            }
        }
        return nearestIndex;
    }

    private double calculateEuclideanDistance(UserVector v1, UserVector v2) {
        double[] c1 = v1.components();
        double[] c2 = v2.components();
        double sum = 0.0;
        for (int i = 0; i < 10; i++) {
            double diff = c1[i] - c2[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
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