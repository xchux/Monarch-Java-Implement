package com.example.monarch.common.types;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

// Represents the definition of a monitoring metric
@Data
@Builder
public class Metric {
    private String schemaName; // e.g., "/rpc/server/latency"
    private Map<String, Object> fields; // e.g., {service: "MonarchService", command: "Query"}
}
