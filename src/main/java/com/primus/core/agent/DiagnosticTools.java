package com.primus.core.agent;

import com.primus.core.knowledge.KbDocument;
import com.primus.core.knowledge.RetrievalService;
import com.primus.core.memory.IncidentMemory;
import com.primus.core.memory.IncidentMemoryService;
import com.primus.core.observability.FaultSimulator;
import com.primus.core.observability.FaultType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Component
public class DiagnosticTools {

    private final RetrievalService retrievalService;
    private final FaultSimulator faultSimulator;
    private final IncidentMemoryService incidentMemoryService;
    private final Random random = new Random();

    public DiagnosticTools(RetrievalService retrievalService, FaultSimulator faultSimulator, IncidentMemoryService incidentMemoryService) {
        this.retrievalService = retrievalService;
        this.faultSimulator = faultSimulator;
        this.incidentMemoryService = incidentMemoryService;
    }

    public Map<String, Object> getMetrics(String serviceName) {
        return Map.of(
                "serviceName", serviceName,
                "cpuPercent", 40 + random.nextInt(55),
                "memoryPercent", 30 + random.nextInt(60),
                "errorRatePercent", random.nextInt(15),
                "dbConnectionPoolUtilizationPercent", 50 + random.nextInt(50)
        );
    }

    public Map<String, Object> getLogs(String serviceName) {
        FaultType fault = faultSimulator.getFaultType(serviceName);
        List<String> logs = switch (fault) {
            case DEPLOYMENT_ISSUE -> List.of(
                    "[ERROR] Unhandled exception in request handler after deploy v2.4.1",
                    "[ERROR] 500 Internal Server Error spike began immediately following deployment",
                    "[WARN] Deployment v2.4.1 rolled out 3 minutes before error spike started",
                    "[INFO] Health check failing since deployment"
            );
            case CONNECTION_POOL_EXHAUSTION -> List.of(
                    "[ERROR] Connection timeout acquiring resource from pool after 30000ms",
                    "[WARN] Pool utilization at 94%, consider scaling",
                    "[ERROR] Query execution exceeded threshold: 12.4s",
                    "[INFO] Health check passed"
            );
            case TRANSIENT_GLITCH -> List.of(
                    "[WARN] Transient network blip detected, no infrastructure changes present",
                    "[ERROR] Request failed: connection reset by peer",
                    "[INFO] Retrying request, no pattern of recurrence",
                    "[INFO] No underlying resource exhaustion detected"
            );
            case NONE -> List.of("[INFO] Health check passed", "[INFO] No errors detected");
        };
        return Map.of("serviceName", serviceName, "recentLogs", logs);
    }

    public Map<String, Object> searchRunbooks(String query) {
        List<KbDocument> results = retrievalService.findRelevant(query, 2);
        String summary = results.stream()
                .map(doc -> doc.getTitle() + ": " + doc.getContent())
                .collect(Collectors.joining("\n\n"));
        return Map.of("results", summary.isEmpty() ? "No relevant runbooks found." : summary);
    }

    public Map<String, Object> searchIncidentMemory(String query) {
        List<IncidentMemory> results = incidentMemoryService.findSimilarCases(query, 2);
        String summary = results.stream()
                .map(m -> "Past incident: " + m.getSummary() + " | Remediation used: " + m.getRemediationTaken() + " | Outcome: " + m.getOutcome())
                .collect(Collectors.joining("\n\n"));
        return Map.of("results", summary.isEmpty() ? "No similar past incidents found." : summary);
    }

    public Map<String, Object> restartService(String serviceName) {
        boolean healed = faultSimulator.attemptHeal(serviceName, FaultType.TRANSIENT_GLITCH);
        return Map.of("serviceName", serviceName, "action", "restart_service",
                "result", healed ? "Service restarted successfully" : "Service restarted but the issue persists");
    }

    public Map<String, Object> rollbackDeployment(String serviceName) {
        boolean healed = faultSimulator.attemptHeal(serviceName, FaultType.DEPLOYMENT_ISSUE);
        return Map.of("serviceName", serviceName, "action", "rollback_deployment",
                "result", healed ? "Rolled back to previous stable version" : "Rollback completed but the issue persists");
    }

    public Map<String, Object> scaleService(String serviceName) {
        boolean healed = faultSimulator.attemptHeal(serviceName, FaultType.CONNECTION_POOL_EXHAUSTION);
        return Map.of("serviceName", serviceName, "action", "scale_service",
                "result", healed ? "Scaled service to additional replicas" : "Scaled service but the issue persists");
    }

    public boolean checkServiceHealth(String serviceName) {
        return faultSimulator.isHealthy(serviceName);
    }
}