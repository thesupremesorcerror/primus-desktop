package com.primus.core.eval;

import com.primus.core.agent.DiagnosticTools;
import com.primus.core.diagnosis.GeminiClient;
import com.primus.core.knowledge.KbDocument;
import com.primus.core.knowledge.RetrievalService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class LlmRagDiagnoser {

    private final GeminiClient geminiClient;
    private final DiagnosticTools diagnosticTools;
    private final RetrievalService retrievalService;

    public LlmRagDiagnoser(GeminiClient geminiClient, DiagnosticTools diagnosticTools, RetrievalService retrievalService) {
        this.geminiClient = geminiClient;
        this.diagnosticTools = diagnosticTools;
        this.retrievalService = retrievalService;
    }

    public String name() { return "llm_rag"; }

    public EvalResult diagnose(String title, String severity, String serviceName) {
        String searchQuery = title + " " + serviceName;
        List<KbDocument> relevantDocs = retrievalService.findRelevant(searchQuery, 2);
        String knowledgeContext = relevantDocs.isEmpty()
                ? "No relevant runbooks found."
                : relevantDocs.stream()
                .map(doc -> "--- " + doc.getTitle() + " ---\n" + doc.getContent())
                .collect(Collectors.joining("\n\n"));

        String prompt = """
            An incident has occurred. You have no access to live logs or metrics, but
            relevant runbook excerpts are provided below. Based on the incident info and
            runbooks, provide:
            1. A likely root cause hypothesis (1-2 sentences).
            2. A single recommended action: exactly one of restart_service, rollback_deployment, or scale_service.

            Respond in this exact format:
            DIAGNOSIS: <your hypothesis>
            ACTION: <one of restart_service, rollback_deployment, scale_service>

            Incident title: %s
            Severity: %s
            Affected service: %s

            Relevant runbooks:
            %s
            """.formatted(title, severity, serviceName, knowledgeContext);

        String rawResponse = geminiClient.generateDiagnosis(prompt);
        ParsedResponse parsed = parse(rawResponse);

        String observedScenario = observeScenario(serviceName);

        Object toolResult = executeAction(parsed.action, serviceName);
        boolean healthy = diagnosticTools.checkServiceHealth(serviceName);

        return new EvalResult(name(), serviceName, observedScenario, parsed.diagnosis,
                parsed.action, healthy, 1, 0L);
    }

    @SuppressWarnings("unchecked")
    private String observeScenario(String serviceName) {
        Map<String, Object> logsResult = diagnosticTools.getLogs(serviceName);
        List<String> logs = (List<String>) logsResult.get("recentLogs");
        String joined = String.join(" ", logs).toLowerCase();
        if (joined.contains("deploy")) return "BAD_DEPLOYMENT";
        if (joined.contains("connection") || joined.contains("pool")) return "CONNECTION_POOL";
        return "UNKNOWN";
    }

    private Object executeAction(String action, String serviceName) {
        return switch (action) {
            case "restart_service" -> diagnosticTools.restartService(serviceName);
            case "rollback_deployment" -> diagnosticTools.rollbackDeployment(serviceName);
            case "scale_service" -> diagnosticTools.scaleService(serviceName);
            default -> Map.of("status", "no action taken");
        };
    }

    private record ParsedResponse(String diagnosis, String action) {}

    private ParsedResponse parse(String raw) {
        String diagnosis = extractAfter(raw, "DIAGNOSIS:");
        String action = extractAfter(raw, "ACTION:").trim().toLowerCase();
        if (!List.of("restart_service", "rollback_deployment", "scale_service").contains(action)) {
            action = "none";
        }
        return new ParsedResponse(diagnosis, action);
    }

    private String extractAfter(String raw, String marker) {
        int idx = raw.indexOf(marker);
        if (idx == -1) return "";
        String rest = raw.substring(idx + marker.length()).trim();
        int newlineIdx = rest.indexOf('\n');
        return newlineIdx == -1 ? rest : rest.substring(0, newlineIdx).trim();
    }
}