package com.example.monarch.common.types;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

// Represents a monitored entity, e.g., a service instance or a VM
@Data
@Builder
public class Target {
    private String schemaName; // e.g., "ComputeTask"
    private Map<String, Object> fields; // e.g., {user: "monarch", job: "mixer.zone1", cluster: "us-central1", task_num: 183}
}
