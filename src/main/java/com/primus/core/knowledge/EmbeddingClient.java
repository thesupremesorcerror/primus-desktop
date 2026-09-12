package com.primus.core.knowledge;

import com.primus.core.diagnosis.GeminiCallCounter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Outbound client for Gemini's embeddings endpoint.
 *
 * Uses the JDK's built-in java.net.http.HttpClient rather than Spring's
 * RestClient, for the same reason as GeminiClient: this module should carry
 * zero dependency on the spring-web family of artifacts. This is a call to
 * an external service (Gemini), unrelated to the JavaFX <-> Primus Core
 * boundary, which never uses HTTP.
 */
@Component
public class EmbeddingClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final GeminiCallCounter callCounter;

    public EmbeddingClient(@Value("${primus.gemini.api-key}") String apiKey, GeminiCallCounter callCounter) {
        this.apiKey = apiKey;
        this.callCounter = callCounter;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @SuppressWarnings("unchecked")
    public List<Double> embed(String text) {
        Map<String, Object> requestBody = Map.of(
                "content", Map.of(
                        "parts", List.of(Map.of("text", text))
                )
        );

        callCounter.increment();
        try {
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/models/gemini-embedding-001:embedContent"))
                    .header("X-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new RuntimeException("Gemini embeddings API error " + response.statusCode() + ": " + response.body());
            }

            Map<String, Object> responseBody = objectMapper.readValue(response.body(), Map.class);
            Map<String, Object> embedding = (Map<String, Object>) responseBody.get("embedding");
            return (List<Double>) embedding.get("values");
        } catch (java.io.IOException e) {
            throw new RuntimeException("Gemini embeddings API call failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Gemini embeddings API call interrupted", e);
        }
    }
}