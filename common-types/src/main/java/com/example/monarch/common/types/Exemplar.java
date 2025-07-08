package com.example.monarch.common.types;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class Exemplar {
    private double value;
    private long timestamp;
    private String traceId; // e.g., Dapper RPC trace
    private Map<String, Object> fields; // Originating target and metric fields
}
