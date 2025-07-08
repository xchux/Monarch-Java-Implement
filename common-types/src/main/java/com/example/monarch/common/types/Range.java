package com.example.monarch.common.types;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

// Represents a range of target strings
@Data
@Builder
@AllArgsConstructor
public class Range {
    private String startTargetString; // Inclusive
    private String endTargetString;   // Exclusive
}
