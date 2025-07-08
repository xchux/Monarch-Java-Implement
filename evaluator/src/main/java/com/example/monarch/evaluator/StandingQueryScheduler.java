package com.example.monarch.evaluator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StandingQueryScheduler {

    private final WebClient rootMixerClient;
    private final Map<String, String> standingQueries = new ConcurrentHashMap<>(); // queryId -> queryJson

    public StandingQueryScheduler(WebClient.Builder webClientBuilder, @Value("${root.mixer.url}") String rootMixerUrl) {
        this.rootMixerClient = webClientBuilder.baseUrl(rootMixerUrl).build();
    }

    public void addStandingQuery(String queryId, String queryJson) {
        standingQueries.put(queryId, queryJson);
    }

    public void removeStandingQuery(String queryId) {
        standingQueries.remove(queryId);
    }

    @Scheduled(fixedRate = 5000) // Execute every 5 seconds
    public void executeStandingQueries() {
        if (standingQueries.isEmpty()) {
            return;
        }
        System.out.println("Executing standing queries...");
        standingQueries.forEach((queryId, queryJson) -> {
            rootMixerClient.post()
                    .uri("/root/v1/query")
                    .bodyValue(queryJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(response -> System.out.println("Query " + queryId + " result: " + response),
                               error -> System.err.println("Error executing query " + queryId + ": " + error.getMessage()));
        });
    }
}
