package com.primus.core.knowledge;

import tools.jackson.databind.ObjectMapper;

import java.util.List;

public class EmbeddingSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String toJson(List<Double> embedding) {
        try {
            return MAPPER.writeValueAsString(embedding);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize embedding", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Double> fromJson(String json) {
        try {
            return MAPPER.readValue(json, List.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize embedding", e);
        }
    }
}