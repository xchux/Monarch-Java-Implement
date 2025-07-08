package com.example.monarch.common.types;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

// Distribution (histogram) implementation
// Consider introducing high-performance libraries like HdrHistogram
@Data
@Builder
public class DistributionValue {
    private long count;
    private double sum;
    private double sumOfSquares; // For standard deviation
    private Map<Bucket, BucketStats> buckets;
    private Exemplar exemplar;

    // Inner classes for bucket and stats, assuming they are needed for the map key/value
    @Data
    @Builder
    public static class Bucket {
        // Define bucket properties, e.g., upper bound
        private double upperBound;
    }

    @Data
    @Builder
    public static class BucketStats {
        private long count;
    }
}
