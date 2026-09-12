package com.primus.core.eval;

public record EvalResult(
        String systemName,
        String serviceName,
        String observedScenario,   // ground truth: CONNECTION_POOL, BAD_DEPLOYMENT, TRANSIENT_GLITCH, or UNKNOWN
        String diagnosis,
        String proposedAction,
        boolean resolved,          // checkServiceHealth() after action
        int llmCallCount,          // cost proxy
        long latencyMs
) {
    public boolean diagnosisCorrect() {
        String lower = diagnosis.toLowerCase();
        if (observedScenario.equals("CONNECTION_POOL")) {
            return lower.contains("connection") || lower.contains("pool");
        } else if (observedScenario.equals("BAD_DEPLOYMENT")) {
            return lower.contains("deploy");
        } else if (observedScenario.equals("TRANSIENT_GLITCH")) {
            return lower.contains("transient") || lower.contains("reset") || lower.contains("intermittent");
        }
        return false;
    }

    public boolean remediationCorrect() {
        if (observedScenario.equals("CONNECTION_POOL")) {
            return proposedAction.equals("scale_service");
        } else if (observedScenario.equals("BAD_DEPLOYMENT")) {
            return proposedAction.equals("rollback_deployment");
        } else if (observedScenario.equals("TRANSIENT_GLITCH")) {
            return proposedAction.equals("restart_service");
        }
        return false;
    }
}