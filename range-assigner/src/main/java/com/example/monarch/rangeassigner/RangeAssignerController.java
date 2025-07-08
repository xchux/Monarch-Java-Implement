package com.example.monarch.rangeassigner;

import com.example.monarch.common.types.Range;
import com.example.monarch.common.types.RangeAssignment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/range-assigner/v1")
public class RangeAssignerController {

    // In-memory store for registered leaves (LeafId -> LeafUrl)
    private final Map<Integer, String> registeredLeaves = new ConcurrentHashMap<>();
    // In-memory store for range assignments (Range -> LeafId)
    private final Map<Range, Integer> rangeAssignments = new ConcurrentHashMap<>();

    private final AtomicInteger nextLeafId = new AtomicInteger(1);

    public RangeAssignerController() {
        // Initialize with a single, full range assigned to a conceptual 'null' leaf
        // This range will be split and reassigned as leaves register
        rangeAssignments.put(new Range("", "\uffff"), 0); // LeafId 0 means unassigned/initial
    }

    @PostMapping("/registerLeaf")
    public Mono<Integer> registerLeaf(@RequestBody String leafUrl) {
        int newLeafId = nextLeafId.getAndIncrement();
        registeredLeaves.put(newLeafId, leafUrl);
        System.out.println("Registered Leaf: " + newLeafId + " at " + leafUrl);

        // Find a range to split and reassign
        // For simplicity, we'll always split the first available range (which initially is the full range)
        Range rangeToSplit = rangeAssignments.keySet().stream()
                .filter(range -> rangeAssignments.get(range) == 0) // Find an unassigned range
                .findFirst()
                .orElse(null);

        if (rangeToSplit == null) {
            // If no unassigned range, try to split an existing assigned range
            // This is a very basic rebalancing. In a real system, this would be more complex.
            rangeToSplit = rangeAssignments.keySet().stream()
                    .max(Comparator.comparingInt(r -> r.getEndTargetString().length() - r.getStartTargetString().length()))
                    .orElse(new Range("", "\uffff")); // Fallback to full range if no ranges exist
        }

        splitAndAssignRange(rangeToSplit, newLeafId);

        return Mono.just(newLeafId);
    }

    private void splitAndAssignRange(Range originalRange, int newLeafId) {
        // Remove the original range
        rangeAssignments.remove(originalRange);

        String start = originalRange.getStartTargetString();
        String end = originalRange.getEndTargetString();

        // Simple split: find the midpoint of the string representation
        // This is a naive split and needs more robust logic for real lexicographical sharding
        String midpoint = findMidpoint(start, end);

        Range newRange1 = new Range(start, midpoint);
        Range newRange2 = new Range(midpoint, end);

        // Assign one half to the new leaf, and the other half to an existing leaf (or keep unassigned)
        // For now, assign newRange1 to the new leaf, and newRange2 to leaf 0 (unassigned) or an existing leaf
        rangeAssignments.put(newRange1, newLeafId);
        // If there are other leaves, distribute newRange2 to one of them, otherwise keep it unassigned (leaf 0)
        int existingLeafId = registeredLeaves.keySet().stream().filter(id -> id != newLeafId).findAny().orElse(0);
        rangeAssignments.put(newRange2, existingLeafId);

        System.out.println("Split range " + originalRange + " into " + newRange1 + " (assigned to " + newLeafId + ") and " + newRange2 + " (assigned to " + existingLeafId + ")");
    }

    // Naive midpoint finder for lexicographical strings
    private String findMidpoint(String s1, String s2) {
        // This is a very simplified version. A real implementation would handle varying lengths and character sets.
        // For now, it assumes ASCII and finds a character in the middle.
        if (s1.isEmpty() && s2.isEmpty()) return "m";
        if (s1.isEmpty()) s1 = "\u0000"; // Smallest possible char
        if (s2.isEmpty()) s2 = "\uffff"; // Largest possible char

        int len = Math.max(s1.length(), s2.length());
        StringBuilder midpoint = new StringBuilder();
        for (int i = 0; i < len; i++) {
            char c1 = (i < s1.length()) ? s1.charAt(i) : '\u0000';
            char c2 = (i < s2.length()) ? s2.charAt(i) : '\uffff';

            if (c1 == c2) {
                midpoint.append(c1);
            } else {
                char midChar = (char) ((c1 + c2) / 2);
                midpoint.append(midChar);
                break;
            }
        }
        return midpoint.toString();
    }

    @GetMapping("/getAssignments")
    public Mono<List<RangeAssignment>> getAssignments() {
        List<RangeAssignment> assignments = rangeAssignments.entrySet().stream()
                .map(entry -> new RangeAssignment(entry.getKey(), entry.getValue(), registeredLeaves.get(entry.getValue())))
                .collect(Collectors.toList());
        System.out.println("Returning assignments: " + assignments);
        return Mono.just(assignments);
    }

    @PostMapping("/assign")
    public Mono<String> assignRanges() {
        System.out.println("Range Assigner received assignment request. Current leaves: " + registeredLeaves.size());
        return Mono.just("Range assignment simulated. Current assignments: " + rangeAssignments.size());
    }
}
