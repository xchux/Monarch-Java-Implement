package com.example.monarch.leafrouter;

import com.example.monarch.common.types.RangeAssignment;
import com.example.monarch.common.types.TimeSeries;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1")
public class IngestionRouterController {

    private final WebClient rangeAssignerClient;

    @Value("${leaf.service.url:http://localhost:8080}")
    private String defaultLeafServiceUrl; // Fallback if no assignment

    @Value("${range.assigner.url:http://localhost:8085}")
    private String rangeAssignerUrl;

    // In-memory cache of range assignments
    private final AtomicReference<List<RangeAssignment>> currentAssignments = new AtomicReference<>();

    public IngestionRouterController(WebClient.Builder webClientBuilder) {
        this.rangeAssignerClient = webClientBuilder.baseUrl(rangeAssignerUrl).build();
    }

    @PostConstruct
    public void fetchRangeAssignments() {
        // Periodically fetch assignments for dynamic rebalancing
        // For simplicity, fetching once at startup for now
        rangeAssignerClient.get()
                .uri("/range-assigner/v1/getAssignments")
                .retrieve()
                .bodyToFlux(RangeAssignment.class)
                .collectList()
                .subscribe(assignments -> {
                    currentAssignments.set(assignments);
                    System.out.println("Fetched range assignments: " + assignments);
                },
                error -> System.err.println("Error fetching range assignments: " + error.getMessage()));
    }

    @PostMapping("/write")
    public Mono<String> routeTimeSeries(@RequestBody TimeSeries timeSeries) {
        System.out.println("Ingestion Router received TimeSeries.");

        String targetString = generateTargetString(timeSeries);
        String targetLeafUrl = defaultLeafServiceUrl;

        List<RangeAssignment> assignments = currentAssignments.get();
        if (assignments != null && !assignments.isEmpty()) {
            // Find the correct leaf based on the target string
            targetLeafUrl = assignments.stream()
                    .filter(assignment -> {
                        String start = assignment.getRange().getStartTargetString();
                        String end = assignment.getRange().getEndTargetString();
                        return targetString.compareTo(start) >= 0 && targetString.compareTo(end) < 0;
                    })
                    .map(RangeAssignment::getLeafUrl)
                    .findFirst()
                    .orElse(defaultLeafServiceUrl);
        }

        System.out.println("Forwarding TimeSeries with target string '" + targetString + "' to Leaf Service at: " + targetLeafUrl);

        return WebClient.builder().baseUrl(targetLeafUrl).build().post()
                .uri("/leaf/v1/write")
                .bodyValue(timeSeries)
                .retrieve()
                .bodyToMono(String.class);
    }

    private String generateTargetString(TimeSeries timeSeries) {
        // This is a simplified version. In a real Monarch system, this would be more robust.
        // It concatenates schemaName and sorted field values.
        StringBuilder sb = new StringBuilder();
        sb.append(timeSeries.getTarget().getSchemaName());
        timeSeries.getTarget().getFields().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sb.append("_").append(entry.getValue()));
        return sb.toString();
    }
}
