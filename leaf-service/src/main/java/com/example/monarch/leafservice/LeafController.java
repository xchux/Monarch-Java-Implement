package com.example.monarch.leafservice;

import com.example.monarch.common.types.Metric;
import com.example.monarch.common.types.Target;
import com.example.monarch.common.types.TimeSeries;
import com.example.monarch.common.types.TimeSeriesPoint;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/leaf/v1")
public class LeafController {

    // In-memory storage for time series data: Key is TargetString + MetricSchemaName
    private final ConcurrentHashMap<String, TimeSeries> timeSeriesStore = new ConcurrentHashMap<>();
    private final WebClient zoneIndexWebClient;
    private final WebClient rangeAssignerWebClient;
    private final WebClient configServerWebClient;
    private final KafkaTemplate<String, TimeSeriesPoint> kafkaTemplate;

    private Integer leafId; // This will be assigned by the Range Assigner

    private static final String RECOVERY_LOG_TOPIC = "monarch-recovery-logs";

    private String mySettingConfigValue; // Local cache for configuration

    public LeafController(WebClient zoneIndexWebClient, WebClient rangeAssignerWebClient, WebClient configServerWebClient, KafkaTemplate<String, TimeSeriesPoint> kafkaTemplate) {
        this.zoneIndexWebClient = zoneIndexWebClient;
        this.rangeAssignerWebClient = rangeAssignerWebClient;
        this.configServerWebClient = configServerWebClient;
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostConstruct
    public void registerWithRangeAssigner() {
        rangeAssignerWebClient.post()
                .uri("/range-assigner/v1/registerLeaf")
                .bodyValue("http://localhost:" + 8080) // Assuming leaf service runs on 8080
                .retrieve()
                .bodyToMono(Integer.class)
                .subscribe(assignedLeafId -> {
                    this.leafId = assignedLeafId;
                    System.out.println("Registered with Range Assigner. Assigned Leaf ID: " + this.leafId);
                },
                error -> System.err.println("Error registering with Range Assigner: " + error.getMessage()));
    }

    @Scheduled(fixedRate = 10000) // Fetch config every 10 seconds
    public void fetchConfiguration() {
        configServerWebClient.get()
                .uri("/config/v1/my.setting")
                .retrieve()
                .bodyToMono(Map.class) // Assuming the response is a Map (JSON object)
                .subscribe(config -> {
                    if (config != null && config.containsKey("configValue")) {
                        this.mySettingConfigValue = (String) config.get("configValue");
                        System.out.println("Fetched configuration 'my.setting': " + this.mySettingConfigValue);
                    }
                },
                error -> System.err.println("Error fetching configuration: " + error.getMessage()));
    }

    @PostMapping("/write")
    public Mono<String> writeTimeSeries(@RequestBody TimeSeries timeSeries) {
        // In a real scenario, this would be the Target String
        String key = generateTimeSeriesKey(timeSeries);

        timeSeriesStore.compute(key, (k, existingTimeSeries) -> {
            if (existingTimeSeries == null) {
                return timeSeries;
            } else {
                // Append new points to existing TimeSeries
                existingTimeSeries.getPoints().addAll(timeSeries.getPoints());
                return existingTimeSeries;
            }
        });

        System.out.println("Received and stored TimeSeries for key: " + key);

        // Send to Kafka as recovery log
        timeSeries.getPoints().forEach(point -> {
            kafkaTemplate.send(RECOVERY_LOG_TOPIC, key, point);
            System.out.println("Sent TimeSeriesPoint to Kafka: " + point);
        });

        // Register hints with Zone Index Server
        if (leafId != null) { // Only register hints if leafId is assigned
            registerHints(timeSeries.getTarget(), timeSeries.getMetric());
        }

        return Mono.just("OK");
    }

    @GetMapping("/data")
    public Mono<List<TimeSeriesPoint>> getData(
            @RequestParam String metricSchemaName,
            @RequestParam long startTime,
            @RequestParam long endTime) {
        System.out.println("Leaf received data request for metric: " + metricSchemaName + ", from " + startTime + " to " + endTime);

        List<TimeSeriesPoint> resultPoints = new ArrayList<>();

        // Iterate through all stored TimeSeries to find relevant data
        timeSeriesStore.forEach((key, timeSeries) -> {
            if (timeSeries.getMetric().getSchemaName().equals(metricSchemaName)) {
                timeSeries.getPoints().stream()
                        .filter(point -> point.getTimestamp() >= startTime && point.getTimestamp() <= endTime)
                        .forEach(resultPoints::add);
            }
        });

        // Sort points by timestamp
        Collections.sort(resultPoints, Comparator.comparingLong(TimeSeriesPoint::getTimestamp));

        return Mono.just(resultPoints);
    }

    private String generateTimeSeriesKey(TimeSeries timeSeries) {
        // This is a simplified version of a Monarch Target String + Metric Schema Name
        StringBuilder sb = new StringBuilder();
        sb.append(timeSeries.getTarget().getSchemaName());
        timeSeries.getTarget().getFields().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sb.append("_").append(entry.getValue()));
        sb.append("_").append(timeSeries.getMetric().getSchemaName());
        return sb.toString();
    }

    private void registerHints(Target target, Metric metric) {
        Map<Long, Set<Integer>> hints = new HashMap<>();

        // Generate trigrams for target fields
        target.getFields().forEach((k, v) -> {
            if (v != null) {
                generateTrigrams(v.toString()).forEach(trigram -> {
                    hints.computeIfAbsent((long) trigram.hashCode(), id -> new HashSet<>()).add(leafId);
                });
            }
        });

        // Generate trigrams for metric fields
        metric.getFields().forEach((k, v) -> {
            if (v != null) {
                generateTrigrams(v.toString()).forEach(trigram -> {
                    hints.computeIfAbsent((long) trigram.hashCode(), id -> new HashSet<>()).add(leafId);
                });
            }
        });

        // Register hints with the Zone Index Server
        zoneIndexWebClient.post()
                .uri("/zone/index/v1/registerHints")
                .bodyValue(hints)
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(response -> System.out.println("Hints registration response: " + response),
                           error -> System.err.println("Error registering hints: " + error.getMessage()));
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
}
