package com.primus.core.agent;

import com.primus.core.diagnosis.GeminiClient;
import com.primus.core.incident.Incident;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractSpecialistAgent implements SpecialistAgent {

    private static final int MAX_ROUNDS = 3;

    protected final GeminiClient geminiClient;
    protected final DiagnosticTools diagnosticTools;

    protected AbstractSpecialistAgent(GeminiClient geminiClient, DiagnosticTools diagnosticTools) {
        this.geminiClient = geminiClient;
        this.diagnosticTools = diagnosticTools;
    }

    protected abstract Set<String> allowedTools();
    protected abstract String systemPromptTemplate(); // expects %s: title, severity, service, context

    @Override
    @SuppressWarnings("unchecked")
    public AgentFinding investigate(Incident incident, String context) {
        List<Map<String, Object>> tools = ToolDefinitions.getFunctionDeclarationsFor(allowedTools());
        List<Map<String, Object>> conversation = new ArrayList<>();
        List<String> toolsCalled = new ArrayList<>();

        String prompt = systemPromptTemplate().formatted(
                incident.getTitle(), incident.getSeverity(), incident.getAffectedService(),
                context == null ? "None yet." : context
        );
        conversation.add(Map.of("role", "user", "parts", List.of(Map.of("text", prompt))));

        for (int round = 0; round < MAX_ROUNDS; round++) {
            Map<String, Object> response = geminiClient.sendConversation(conversation, tools);

            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            Map<String, Object> firstPart = parts.get(0);

            if (firstPart.containsKey("functionCall")) {
                Map<String, Object> functionCall = (Map<String, Object>) firstPart.get("functionCall");
                String toolName = (String) functionCall.get("name");
                Map<String, Object> args = (Map<String, Object>) functionCall.get("args");

                Object toolResult = allowedTools().contains(toolName)
                        ? executeTool(toolName, args)
                        : Map.of("error", name() + " is not authorized to call " + toolName);

                if (allowedTools().contains(toolName)) toolsCalled.add(toolName);

                conversation.add(Map.of("role", "model", "parts", List.of(firstPart)));
                conversation.add(Map.of("role", "user", "parts", List.of(Map.of(
                        "functionResponse", Map.of("name", toolName, "response", Map.of("result", toolResult))
                ))));
            } else {
                String rawText = (String) firstPart.get("text");
                return AgentFindingParser.parse(name(), toolsCalled, rawText);
            }
        }

        return new AgentFinding(name(), 0.0,
                "No conclusion reached within " + MAX_ROUNDS + " rounds.", List.of(), toolsCalled);
    }

    private Object executeTool(String toolName, Map<String, Object> args) {
        String serviceName = (String) args.get("serviceName");
        return switch (toolName) {
            case "get_metrics" -> diagnosticTools.getMetrics(serviceName);
            case "get_logs" -> diagnosticTools.getLogs(serviceName);
            case "search_runbooks" -> diagnosticTools.searchRunbooks((String) args.get("query"));
            case "search_incident_memory" -> diagnosticTools.searchIncidentMemory((String) args.get("query"));
            default -> Map.of("error", "Unknown tool: " + toolName);
        };
    }
}
