package com.primus.core.agent;

import com.primus.core.diagnosis.GeminiClient;
import com.primus.core.incident.Incident;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DiagnosisAgent {

    private final GeminiClient geminiClient;

    public DiagnosisAgent(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
    }

    public String name() { return "diagnosis_agent"; }

    @SuppressWarnings("unchecked")
    public AgentFinding synthesize(Incident incident, List<AgentFinding> findings) {
        String findingsSummary = findings.stream()
                .map(f -> "[%s] confidence=%.2f: %s".formatted(f.agent(), f.confidence(), f.summary()))
                .reduce("", (a, b) -> a + "\n" + b);

        String prompt = """
            You are the Diagnosis Agent. You do not have tools. Given findings gathered
            by other specialist agents, synthesize a single root-cause hypothesis.
            Respond ONLY with JSON, no other text, matching:
            {"confidence": 0.0-1.0, "summary": "root cause hypothesis, 1-2 sentences", "evidence": [{"type":"synthesis","detail":"which findings this draws from","source":"diagnosis_agent"}]}

            Incident: %s | Severity: %s | Service: %s

            Findings from specialist agents:
            %s
            """.formatted(incident.getTitle(), incident.getSeverity(), incident.getAffectedService(), findingsSummary);

        Map<String, Object> response = geminiClient.sendConversation(
                List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                List.of() // no tools for this agent
        );

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        String rawText = (String) parts.get(0).get("text");

        return AgentFindingParser.parse(name(), List.of(), rawText);
    }
}
