package com.primus.core.agent;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AgentFindingParser {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{.*\\}", Pattern.DOTALL);

    public static AgentFinding parse(String agentName, List<String> toolsCalled, String raw) {
        try {
            Matcher m = JSON_BLOCK.matcher(raw);
            String json = m.find() ? m.group() : raw;
            var node = MAPPER.readTree(json);

            double confidence = node.path("confidence").asDouble(0.0);
            String summary = node.path("summary").asText("");

            List<AgentFinding.Evidence> evidence = MAPPER.convertValue(
                    node.path("evidence"),
                    MAPPER.getTypeFactory().constructCollectionType(List.class, AgentFinding.Evidence.class)
            );

            return new AgentFinding(agentName, confidence, summary, evidence, toolsCalled);
        } catch (Exception e) {
            return new AgentFinding(agentName, 0.0,
                    "Failed to parse response: " + e.getMessage(), List.of(), toolsCalled);
        }
    }
}