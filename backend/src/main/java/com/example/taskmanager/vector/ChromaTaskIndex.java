package com.example.taskmanager.vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ChromaTaskIndex {
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final CircuitBreaker circuitBreaker;
    private final boolean enabled;
    private final String baseUrl;
    private final String collectionName;
    private volatile String collectionId;

    public ChromaTaskIndex(
            ObjectMapper mapper,
            CircuitBreakerRegistry circuitBreakerRegistry,
            @Value("${app.vector.enabled:false}") boolean enabled,
            @Value("${app.vector.base-url}") String baseUrl,
            @Value("${app.vector.collection}") String collectionName) {
        this.mapper = mapper;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("chroma");
        this.enabled = enabled;
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.collectionName = collectionName;
    }

    @Async("indexingExecutor")
    @EventListener
    public void handle(TaskIndexEvent event) {
        if (!enabled) {
            return;
        }
        try {
            circuitBreaker.executeRunnable(() -> {
                if (event.operation() == TaskIndexEvent.Operation.DELETE) {
                    delete(event.id());
                } else {
                    upsert(event);
                }
            });
        } catch (RuntimeException ignored) {
            // PostgreSQL commit is authoritative; the reconciler repairs missed indexing.
        }
    }

    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "chroma")
    public List<Long> search(String query, int limit) {
        if (!enabled) {
            throw new IllegalStateException("Vector search is disabled");
        }
        JsonNode response = post("/collections/" + collectionId() + "/query", Map.of(
                "query_texts", List.of(query),
                "n_results", limit,
                "include", List.of("distances", "metadatas")));
        List<Long> ids = new ArrayList<>();
        response.path("ids").path(0).forEach(node -> ids.add(Long.parseLong(node.asText())));
        return ids;
    }

    private void upsert(TaskIndexEvent event) {
        post("/collections/" + collectionId() + "/upsert", Map.of(
                "ids", List.of(event.id().toString()),
                "documents", List.of(event.document()),
                "metadatas", List.of(event.metadata())));
    }

    private void delete(Long id) {
        post("/collections/" + collectionId() + "/delete", Map.of("ids", List.of(id.toString())));
    }

    private String collectionId() {
        String current = collectionId;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (collectionId == null) {
                JsonNode collection = post("/collections", Map.of(
                        "name", collectionName,
                        "get_or_create", true,
                        "metadata", Map.of("hnsw:space", "cosine")));
                collectionId = collection.path("id").asText();
                if (collectionId.isBlank()) {
                    throw new IllegalStateException("Chroma returned no collection id");
                }
            }
            return collectionId;
        }
    }

    private JsonNode post(String path, Object body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Chroma returned " + response.statusCode());
            }
            return response.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(response.body());
        } catch (Exception exception) {
            throw new IllegalStateException("Chroma request failed", exception);
        }
    }
}
