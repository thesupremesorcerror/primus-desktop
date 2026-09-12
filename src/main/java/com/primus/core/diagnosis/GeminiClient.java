package com.primus.core.diagnosis;

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
 * Outbound client for the Gemini API.
 *
 * Uses the JDK's built-in java.net.http.HttpClient rather than Spring's
 * RestClient/WebClient so that this module carries zero dependency on the
 * spring-web family of artifacts. This call is to an external LLM service --
 * it has nothing to do with the JavaFX UI, which never talks to this class
 * or to Gemini directly (see Orchestrator / DiagnosisAgent / DiagnosisService).
 */
@Component
public class GeminiClient {

    private static final long MIN_INTERVAL_MS = 4500; // ~13 req/min, safely under 15/min free tier cap
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private volatile long lastCallTime = 0;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final GeminiCallCounter callCounter;

    public GeminiClient(@Value("${primus.gemini.api-key}") String apiKey, GeminiCallCounter callCounter) {
        this.apiKey = apiKey;
        this.callCounter = callCounter;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String generateDiagnosis(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                )
        );
        Map<String, Object> response = callGemini(requestBody);
        return extractText(response);
    }

    public Map<String, Object> sendConversation(List<Map<String, Object>> contents, List<Map<String, Object>> tools) {
        Map<String, Object> requestBody = Map.of(
                "contents", contents,
                "tools", List.of(Map.of("functionDeclarations", tools))
        );
        return callGemini(requestBody);
    }

    private synchronized void throttle() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastCallTime;
        if (elapsed < MIN_INTERVAL_MS) {
            try {
                Thread.sleep(MIN_INTERVAL_MS - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallTime = System.currentTimeMillis();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callGemini(Map<String, Object> requestBody) {
        throttle();
        callCounter.increment();
        try {
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/models/gemini-3.1-flash-lite:generateContent"))
                    .header("X-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new RuntimeException("Gemini API error " + response.statusCode() + ": " + response.body());
            }

            return objectMapper.readValue(response.body(), Map.class);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Gemini API call failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Gemini API call interrupted", e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        return (String) parts.get(0).get("text");
    }
}