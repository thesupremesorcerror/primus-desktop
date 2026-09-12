package com.primus.core.eval;

import com.primus.core.agent.DiagnosticTools;
import com.primus.core.diagnosis.GeminiClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LlmNoRagDiagnoser {

    private final GeminiClient geminiClient;
    private final DiagnosticTools diagnosticTools;

    public LlmNoRagDiagnoser(GeminiClient geminiClient, DiagnosticTools diagnosticTools) {
        this.geminiClient = geminiClient;
        this.diagnosticTools = diagnosticTools;
    }

    public String name() { return "llm_no_rag"; }

    public EvalResult diagnose(String title, String severity, String serviceName) {
        String prompt = """
            An incident has occurred. Based only on the information below (you have no
            access to logs, metrics, or runbooks), provide:
            1. A likely root cause hypothesis (1-2 sentences).
            2. A single recommended action: exactly one of restart_service, rollback_deployment, or scale_service.

            Respond in this exact format:
            DIAGNOSIS: <your hypothesis>
            ACTION: <one of restart_service, rollback_deployment, scale_service>

            Incident title: %s
            Severity: %s
            Affected service: %s
            """.formatted(title, severity, serviceName);

        String rawResponse = geminiClient.generateDiagnosis(prompt);
        ParsedResponse parsed = parse(rawResponse);

        // Read ground truth from what getLogs() actually returns for this run
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