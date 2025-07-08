package com.example.monarch.zonemixer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/zone/v1")
public class ZoneMixerController {

    private final WebClient zoneIndexWebClient;
    private final WebClient leafServiceWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${zone.index.server.url:http://localhost:8082}")
    private String zoneIndexServerUrl;

    @Value("${leaf.service.url:http://localhost:8080}")
    private String leafServiceUrl;

    public ZoneMixerController(WebClient.Builder webClientBuilder) {
        this.zoneIndexWebClient = webClientBuilder.baseUrl(zoneIndexServerUrl).build();
        this.leafServiceWebClient = webClientBuilder.baseUrl(leafServiceUrl).build();
    }

    @PostMapping("/query")
    public Mono<String> queryTimeSeries(@RequestBody String queryJson) {
        System.out.println("Zone Mixer received query: " + queryJson);

        return Mono.just(queryJson)
                .map(this::extractTrigramsFromQuery)
                .flatMap(this::getRelevantLeafIdsFromIndexServer)
                .flatMap(this::fanOutToLeaves)
                .map(results -> "Query processed by Zone Mixer. Results from leaves: " + results)
                .onErrorResume(e -> Mono.just("Error processing query: " + e.getMessage()));
    }

    @GetMapping("/aggregate")
    public Mono<String> aggregateMetric(
            @RequestParam String metricSchemaName,
            @RequestParam long startTime,
            @RequestParam long endTime,
            @RequestParam(defaultValue = "sum") String aggregationType) {
        System.out.println("Zone Mixer received aggregation request for metric: " + metricSchemaName +
                           ", from " + startTime + " to " + endTime + ", type: " + aggregationType);

        return leafServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/leaf/v1/data")
                        .queryParam("metricSchemaName", metricSchemaName)
                        .queryParam("startTime", startTime)
                        .queryParam("endTime", endTime)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(leafData -> {
                    double result = 0.0;
                    long count = 0;
                    double min = Double.MAX_VALUE;
                    double max = Double.MIN_VALUE;

                    try {
                        JsonNode root = objectMapper.readTree(leafData);
                        if (root.isArray()) {
                            for (JsonNode node : root) {
                                if (node.has("value") && node.get("value").isNumber()) {
                                    double value = node.get("value").asDouble();
                                    result += value; // For sum and average
                                    count++;
                                    min = Math.min(min, value);
                                    max = Math.max(max, value);
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing leaf data for aggregation: " + e.getMessage());
                        return "Error during aggregation: " + e.getMessage();
                    }

                    switch (aggregationType.toLowerCase()) {
                        case "sum":
                            return "Aggregation result (sum) for " + metricSchemaName + ": " + result;
                        case "avg":
                            return "Aggregation result (average) for " + metricSchemaName + ": " + (count > 0 ? result / count : 0.0);
                        case "min":
                            return "Aggregation result (min) for " + metricSchemaName + ": " + (count > 0 ? min : 0.0);
                        case "max":
                            return "Aggregation result (max) for " + metricSchemaName + ": " + (count > 0 ? max : 0.0);
                        case "count":
                            return "Aggregation result (count) for " + metricSchemaName + ": " + count;
                        default:
                            return "Unsupported aggregation type: " + aggregationType;
                    }
                })
                .onErrorResume(e -> Mono.just("Error during aggregation: " + e.getMessage()));
    }

    private Set<Long> extractTrigramsFromQuery(String queryJson) {
        Set<Long> trigramFingerprints = new HashSet<>();
        try {
            JsonNode rootNode = objectMapper.readTree(queryJson);
            // For simplicity, assume a 'filter' field exists and extract trigrams from its string value
            JsonNode filterNode = rootNode.path("operations").get(0).path("inputs").get(0).path("operations").get(1).path("predicate");
            if (filterNode.isTextual()) {
                String filterString = filterNode.asText();
                generateTrigrams(filterString).forEach(trigram -> trigramFingerprints.add((long) trigram.hashCode()));
            }
        } catch (Exception e) {
            System.err.println("Error extracting trigrams from query: " + e.getMessage());
        }
        return trigramFingerprints;
    }

    private Set<String> generateTrigrams(String text) {
        Set<String> trigrams = new HashSet<>();
        if (text == null || text.length() < 3) {
            return trigrams;
        }
        for (int i = 0; i <= text.length() - 3; i++) {
            trigrams.add(text.substring(i, i + 3));
        }
        return trigrams;
    }

    private Mono<Set<Integer>> getRelevantLeafIdsFromIndexServer(Set<Long> trigramFingerprints) {
        if (trigramFingerprints.isEmpty()) {
            // If no trigrams, assume all leaves are relevant for now (or handle as per design)
            return Mono.just(Set.of(1)); // Assuming leafId 1 for now
        }
        // Call Zone Index Server to get relevant leaf IDs
        return zoneIndexWebClient.post()
                .uri("/zone/index/v1/queryHints") // Assuming a queryHints endpoint exists
                .bodyValue(trigramFingerprints)
                .retrieve()
                .bodyToMono(Set.class)
                .map(rawSet -> (Set<Integer>) rawSet)
                .onErrorResume(e -> {
                    System.err.println("Error querying Zone Index Server: " + e.getMessage());
                    return Mono.just(Set.of(1)); // Fallback to all leaves on error
                });
    }

    private Mono<String> fanOutToLeaves(Set<Integer> leafIds) {
        // Simulate fan-out to relevant leaves
        // In a real scenario, this would involve sending sub-queries to each leaf
        return Mono.just("Simulated fan-out to leaves: " + leafIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));
    }
}
