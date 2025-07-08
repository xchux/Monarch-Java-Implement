package com.example.monarch.common.types;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

// Represents the assignment of a Range to a Leaf node
@Data
@Builder
@AllArgsConstructor
public class RangeAssignment {
    private Range range;
    private Integer leafId;
    private String leafUrl; // URL of the assigned Leaf node
}
