package com.primus.core.agent;

import com.primus.core.diagnosis.GeminiClient;
import com.primus.core.incident.Incident;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RemediationAgent {

    private static final Set<String> REMEDIATION_TOOLS =
            Set.of("restart_service", "rollback_deployment", "scale_service");

    private final GeminiClient geminiClient;

    public RemediationAgent(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
    }

    public String name() { return "remediation_agent"; }

    public record Proposal(boolean hasProposal, String toolName, String serviceName, String rationale) {}

    @SuppressWarnings("unchecked")
    public Proposal propose(Incident incident, AgentFinding diagnosis) {
        List<Map<String, Object>> tools =
                ToolDefinitions.getFunctionDeclarationsFor(REMEDIATION_TOOLS);

        String prompt = """
            You are the Remediation Agent. Based on the diagnosed root cause below, call
            EXACTLY ONE remediation tool (restart_service, rollback_deployment, or
            scale_service) that best addresses it. Do not call more than one.

            Incident: %s | Severity: %s | Service: %s
            Diagnosed root cause (confidence %.2f): %s
            """.formatted(incident.getTitle(), incident.getSeverity(), incident.getAffectedService(),
                diagnosis.confidence(), diagnosis.summary());

        List<Map<String, Object>> conversation = List.of(
                Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))
        );

        Map<String, Object> response = geminiClient.sendConversation(conversation, tools);

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        Map<String, Object> firstPart = parts.get(0);

        if (firstPart.containsKey("functionCall")) {
            Map<String, Object> functionCall = (Map<String, Object>) firstPart.get("functionCall");
            String toolName = (String) functionCall.get("name");
            Map<String, Object> args = (Map<String, Object>) functionCall.get("args");
            String serviceName = (String) args.get("serviceName");
            return new Proposal(true, toolName, serviceName, diagnosis.summary());
        }

        return new Proposal(false, null, null, "No remediation action proposed.");
    }
}