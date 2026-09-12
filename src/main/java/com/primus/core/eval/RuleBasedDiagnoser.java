package com.primus.core.eval;

import com.primus.core.agent.DiagnosticTools;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RuleBasedDiagnoser {

    private final DiagnosticTools diagnosticTools;

    public RuleBasedDiagnoser(DiagnosticTools diagnosticTools) {
        this.diagnosticTools = diagnosticTools;
    }

    public String name() { return "rule_based"; }

    @SuppressWarnings("unchecked")
    public EvalResult diagnose(String serviceName) {
        Map<String, Object> logsResult = diagnosticTools.getLogs(serviceName);
        List<String> logs = (List<String>) logsResult.get("recentLogs");
        String joined = String.join(" ", logs).toLowerCase();

        String observedScenario;
        String diagnosis;
        String proposedAction;

        if (joined.contains("deploy")) {
            observedScenario = "BAD_DEPLOYMENT";
            diagnosis = "Recent deployment appears to have introduced a regression.";
            proposedAction = "rollback_deployment";
        } else if (joined.contains("pool")) {
            observedScenario = "CONNECTION_POOL";
            diagnosis = "Database connection pool appears exhausted.";
            proposedAction = "scale_service";
        } else if (joined.contains("connection")) {
            observedScenario = "TRANSIENT_GLITCH";
            diagnosis = "Transient connectivity issue detected, no persistent resource or deployment problem found.";
            proposedAction = "restart_service";
        } else {
            observedScenario = "UNKNOWN";
            diagnosis = "No matching pattern found in logs.";
            proposedAction = "none";
        }

        Object toolResult = executeAction(proposedAction, serviceName);
        boolean healthy = diagnosticTools.checkServiceHealth(serviceName);

        return new EvalResult(name(), serviceName, observedScenario, diagnosis,
                proposedAction, healthy, 1, 0L);
    }

    private Object executeAction(String action, String serviceName) {
        return switch (action) {
            case "restart_service" -> diagnosticTools.restartService(serviceName);
            case "rollback_deployment" -> diagnosticTools.rollbackDeployment(serviceName);
            case "scale_service" -> diagnosticTools.scaleService(serviceName);
            default -> Map.of("status", "no action taken");
        };
    }
}
