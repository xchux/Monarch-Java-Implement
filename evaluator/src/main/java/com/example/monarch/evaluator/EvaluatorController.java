package com.example.monarch.evaluator;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/evaluator/v1")
public class EvaluatorController {

    private final StandingQueryScheduler scheduler;

    public EvaluatorController(StandingQueryScheduler scheduler) {
        this.scheduler = scheduler;
    }

    // Endpoint to register a new standing query
    @PostMapping("/registerStandingQuery")
    public Mono<String> registerStandingQuery(@RequestBody String queryJson) {
        String queryId = "query-" + System.currentTimeMillis(); // Simple ID generation
        scheduler.addStandingQuery(queryId, queryJson);
        System.out.println("Registered standing query with ID: " + queryId);
        return Mono.just("Standing query registered with ID: " + queryId);
    }

    // Endpoint to remove a standing query
    @PostMapping("/removeStandingQuery")
    public Mono<String> removeStandingQuery(@RequestBody String queryId) {
        scheduler.removeStandingQuery(queryId);
        System.out.println("Removed standing query with ID: " + queryId);
        return Mono.just("Standing query removed with ID: " + queryId);
    }
}
