package com.primus.core.agent;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ToolDefinitions {

    public static List<Map<String, Object>> getFunctionDeclarations() {
        return List.of(
                Map.of(
                        "name", "get_metrics",
                        "description", "Get current CPU, memory, error rate, and DB connection pool utilization for a service. Risk: LOW (read-only).",
                        "parameters", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "serviceName", Map.of("type", "STRING", "description", "Name of the affected service")
                                ),
                                "required", List.of("serviceName")
                        )
                ),
                Map.of(
                        "name", "get_logs",
                        "description", "Get recent log lines for a service. Risk: LOW (read-only).",
                        "parameters", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "serviceName", Map.of("type", "STRING", "description", "Name of the affected service")
                                ),
                                "required", List.of("serviceName")
                        )
                ),
                Map.of(
                        "name", "search_runbooks",
                        "description", "Search the knowledge base of runbooks and troubleshooting guides. Risk: LOW (read-only).",
                        "parameters", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "query", Map.of("type", "STRING", "description", "Search query describing the symptom or issue")
                                ),
                                "required", List.of("query")
                        )
                ),
                Map.of(
                        "name", "search_incident_memory",
                        "description", "Search past resolved incidents for similar cases and what remediation worked for them. Risk: LOW (read-only).",
                        "parameters", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "query", Map.of("type", "STRING", "description", "Search query describing the current symptom or issue")
                                ),
                                "required", List.of("query")
                        )
                ),
                Map.of(
                        "name", "restart_service",
                        "description", "Restart a service to clear transient state (e.g. exhausted connections, memory leaks). Risk: MEDIUM.",
                        "parameters", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "serviceName", Map.of("type", "STRING", "description", "Name of the service to restart")
                                ),
                                "required", List.of("serviceName")
                        )
                ),
                Map.of(
                        "name", "rollback_deployment",
                        "description", "Roll back a service to its previous stable deployment version. Use when a recent deployment is the likely cause. Risk: HIGH.",
                        "parameters", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "serviceName", Map.of("type", "STRING", "description", "Name of the service to roll back")
                                ),
                                "required", List.of("serviceName")
                        )
                ),
                Map.of(
                        "name", "scale_service",
                        "description", "Increase the number of replicas for a service to handle load or reduce resource contention. Risk: MEDIUM.",
                        "parameters", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "serviceName", Map.of("type", "STRING", "description", "Name of the service to scale")
                                ),
                                "required", List.of("serviceName")
                        )
                )
        );
    }

    public static List<Map<String, Object>> getFunctionDeclarationsFor(Set<String> allowedNames) {
        return getFunctionDeclarations().stream()
                .filter(decl -> allowedNames.contains(decl.get("name")))
                .toList();
    }

    public static final Map<String, String> RISK_LEVELS = Map.of(
            "get_metrics", "LOW",
            "get_logs", "LOW",
            "search_runbooks", "LOW",
            "search_incident_memory", "LOW",
            "restart_service", "MEDIUM",
            "rollback_deployment", "HIGH",
            "scale_service", "MEDIUM"
    );
}