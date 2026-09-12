package com.primus.core.agent;

import org.springframework.stereotype.Component;

@Component
public class PolicyEngine {

    /**
     * Returns true if the tool call is allowed to execute automatically.
     * LOW risk: always allowed (read-only).
     * MEDIUM risk: allowed automatically for now (per MVP scope) -- a real policy
     *   engine would check predefined rules here (e.g. time of day, blast radius).
     * HIGH risk: never auto-executed -- requires human approval (not yet built,
     *   so for now we just refuse and report that approval would be required).
     * CRITICAL: not exposed as a tool at all (see architecture doc, Section 10).
     */
    public boolean isAutoExecutionAllowed(String toolName) {
        String risk = ToolDefinitions.RISK_LEVELS.getOrDefault(toolName, "HIGH");
        return switch (risk) {
            case "LOW", "MEDIUM" -> true;
            case "HIGH" -> false;
            default -> false;
        };
    }

    public String getRiskLevel(String toolName) {
        return ToolDefinitions.RISK_LEVELS.getOrDefault(toolName, "HIGH");
    }
}
