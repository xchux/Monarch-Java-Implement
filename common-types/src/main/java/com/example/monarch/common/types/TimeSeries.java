package com.example.monarch.common.types;

import lombok.Builder;
import lombok.Data;

import java.util.List;

// Represents a complete time series
@Data
@Builder
public class TimeSeries {
    private Target target;
    private Metric metric;
    // Use an ordered and thread-safe list
    private List<TimeSeriesPoint> points;
}
