package com.example.monarch.common.types;

import lombok.Builder;
import lombok.Data;

// A single data point in a time series
@Data
@Builder
public class TimeSeriesPoint {
    private long timestamp; // UTC milliseconds
    private Object value;     // Can be Boolean, Long, Double, String, or DistributionValue

    // For CUMULATIVE metrics
    private Long startTime;
}
