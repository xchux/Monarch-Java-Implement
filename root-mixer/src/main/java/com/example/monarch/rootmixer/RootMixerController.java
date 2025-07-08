package com.example.monarch.rootmixer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/root/v1")
public class RootMixerController {

    private final List<WebClient> zoneMixerClients;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RootMixerController(WebClient.Builder webClientBuilder, @Value("${zone.mixer.urls}") String zoneMixerUrls) {
        this.zoneMixerClients = Arrays.stream(zoneMixerUrls.split(","))
                .map(webClientBuilder::baseUrl)
                .map(WebClient.Builder::build)
                .collect(Collectors.toList());
    }

    @PostMapping("/query")
    public Mono<String> queryTimeSeries(@RequestBody String queryJson) {
        System.out.println("Root Mixer received query: " + queryJson);

        // For Phase 3, simply forward the query to all configured Zone Mixers
        // In a real scenario, Root Mixer would use Root Index Server to determine relevant zones
        List<Mono<String>> responses = zoneMixerClients.stream()
                .map(client -> client.post()
                        .uri("/zone/v1/query")
                        .bodyValue(queryJson)
                        .retrieve()
                        .bodyToMono(String.class)
                        .onErrorResume(e -> {
                            System.err.println("Error forwarding query to Zone Mixer: " + e.getMessage());
                            return Mono.just("Error from Zone Mixer: " + e.getMessage());
                        }))
                .collect(Collectors.toList());

        return Flux.merge(responses)
                .collectList()
                .map(results -> "Query processed by Root Mixer. Results from zones: " + String.join("; ", results));
    }
}
