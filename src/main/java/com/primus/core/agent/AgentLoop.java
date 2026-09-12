package com.primus.core.agent;

import com.primus.core.diagnosis.GeminiClient;
import com.primus.core.incident.Incident;
import com.primus.core.remediation.RemediationCandidateService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class AgentLoop {

    private static final int MAX_ROUNDS = 6;
    private static final List<String> REMEDIATION_TOOLS = List.of("restart_service", "rollback_deployment", "scale_service");

    private final GeminiClient geminiClient;
    private final DiagnosticTools diagnosticTools;
    private final PolicyEngine policyEngine;
    private final RemediationCandidateService candidateService;

    public AgentLoop(GeminiClient geminiClient, DiagnosticTools diagnosticTools, PolicyEngine policyEngine, RemediationCandidateService candidateService) {
        this.geminiClient = geminiClient;
        this.diagnosticTools = diagnosticTools;
        this.policyEngine = policyEngine;
        this.candidateService = candidateService;
    }

    public static class AgentResult {
        public String diagnosis;
        public String actionTaken;
        public String actionRiskLevel;
        public boolean verified;
        public boolean approvalRequired;
        public UUID incidentId;
    }

    @SuppressWarnings("unchecked")
    public AgentResult diagnose(Incident incident) {
        List<Map<String, Object>> tools = ToolDefinitions.getFunctionDeclarations();
        List<Map<String, Object>> conversation = new ArrayList<>();
        AgentResult result = new AgentResult();
        result.incidentId = incident.getId();

        String initialPrompt = """
                You are an AIOps incident diagnosis and remediation assistant. Investigate this
                incident using the available tools before concluding. Use read-only tools
                (get_metrics, get_logs, search_runbooks, search_incident_memory) first to gather
                evidence. Once you understand the root cause, call exactly ONE remediation tool
                (restart_service, rollback_deployment, or scale_service) to attempt a fix. After
                the remediation tool returns a result, respond with a final plain-text summary
                (2-3 sentences) describing the root cause and the action taken. Do not call more
                than one remediation tool.

                Incident title: %s
                Severity: %s
                Affected service: %s
                """.formatted(incident.getTitle(), incident.getSeverity(), incident.getAffectedService());

        conversation.add(Map.of("role", "user", "parts", List.of(Map.of("text", initialPrompt))));

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

                System.out.println("[AGENT] Round " + (round + 1) + ": calling tool " + toolName + " with args " + args);

                Object toolResult;

                if (REMEDIATION_TOOLS.contains(toolName)) {
                    String risk = policyEngine.getRiskLevel(toolName);
                    if (!policyEngine.isAutoExecutionAllowed(toolName)) {
                        System.out.println("[AGENT] Tool " + toolName + " is " + risk + " risk -- generating candidates for human approval");
                        result.approvalRequired = true;
                        result.actionRiskLevel = risk;
                        String serviceName = (String) args.get("serviceName");
                        candidateService.generateCandidates(incident.getId(), serviceName, toolName);
                        toolResult = Map.of("status", "BLOCKED", "reason", "This action is " + risk + " risk. Candidate remediation options have been generated for human review. Not executed.");
                    } else {
                        System.out.println("[AGENT] Tool " + toolName + " is " + risk + " risk -- auto-executing");
                        toolResult = executeTool(toolName, args);
                        result.actionTaken = toolName + "(" + args.get("serviceName") + ")";
                        result.actionRiskLevel = risk;

                        String serviceName = (String) args.get("serviceName");
                        boolean healthy = diagnosticTools.checkServiceHealth(serviceName);
                        result.verified = healthy;
                        System.out.println("[AGENT] Verification: " + serviceName + " healthy=" + healthy);
                    }
                } else {
                    toolResult = executeTool(toolName, args);
                }

                conversation.add(Map.of("role", "model", "parts", List.of(firstPart)));
                conversation.add(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of(
                                "functionResponse", Map.of("name", toolName, "response", Map.of("result", toolResult))
                        ))
                ));
            } else {
                result.diagnosis = (String) firstPart.get("text");
                System.out.println("[AGENT] Final summary reached after " + (round + 1) + " round(s)");
                return result;
            }
        }

        result.diagnosis = "Agent could not reach a conclusion within " + MAX_ROUNDS + " rounds.";
        return result;
    }

    private Object executeTool(String toolName, Map<String, Object> args) {
        String serviceName = (String) args.get("serviceName");
        return switch (toolName) {
            case "get_metrics" -> diagnosticTools.getMetrics(serviceName);
            case "get_logs" -> diagnosticTools.getLogs(serviceName);
            case "search_runbooks" -> diagnosticTools.searchRunbooks((String) args.get("query"));
            case "restart_service" -> diagnosticTools.restartService(serviceName);
            case "rollback_deployment" -> diagnosticTools.rollbackDeployment(serviceName);
            case "scale_service" -> diagnosticTools.scaleService(serviceName);
            case "search_incident_memory" -> diagnosticTools.searchIncidentMemory((String) args.get("query"));
            default -> Map.of("error", "Unknown tool: " + toolName);
        };
    }
}