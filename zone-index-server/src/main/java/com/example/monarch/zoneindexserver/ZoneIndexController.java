package com.example.monarch.zoneindexserver;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/zone/index/v1")
public class ZoneIndexController {

    // In-memory FHI: fingerprint -> Set<ChildID>
    private final Map<Long, Set<Integer>> fieldHintsIndex = new ConcurrentHashMap<>();

    @PostMapping("/registerHints")
    public Mono<String> registerHints(@RequestBody Map<Long, Set<Integer>> hints) {
        hints.forEach((fingerprint, childIds) -> {
            fieldHintsIndex.computeIfAbsent(fingerprint, k -> ConcurrentHashMap.newKeySet()).addAll(childIds);
        });
        System.out.println("Received and registered hints: " + hints);
        return Mono.just("Hints registered successfully");
    }

    @PostMapping("/queryHints")
    public Mono<Set<Integer>> queryHints(@RequestBody Set<Long> trigramFingerprints) {
        if (trigramFingerprints == null || trigramFingerprints.isEmpty()) {
            // If no trigrams, return all known leaf IDs (for simplicity, assuming leafId 1 for now)
            return Mono.just(Set.of(1));
        }

        Set<Integer> relevantLeafIds = new HashSet<>();
        boolean first = true;

        for (Long fingerprint : trigramFingerprints) {
            Set<Integer> leafIds = fieldHintsIndex.get(fingerprint);
            if (leafIds != null) {
                if (first) {
                    relevantLeafIds.addAll(leafIds);
                    first = false;
                } else {
                    relevantLeafIds.retainAll(leafIds);
                }
            }
        }
        System.out.println("Querying hints for fingerprints: " + trigramFingerprints + ", returning leaf IDs: " + relevantLeafIds);
        return Mono.just(relevantLeafIds);
    }
}
