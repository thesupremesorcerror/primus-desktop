package com.primus.core.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the most recent AgentFinding list produced for each incident's
 * investigation, purely in memory. Orchestrator's findings are otherwise
 * transient (not persisted), so this lets the frontend's Evidence Explorer
 * retrieve what LogAgent/MetricsAgent/RagAgent actually found without
 * requiring a new database table. Cleared on app restart -- acceptable
 * since this is a "what did the last run see" view, not an audit log.
 */
@Component
public class FindingsCache {

    private final Map<UUID, List<AgentFinding>> cache = new ConcurrentHashMap<>();

    public void store(UUID incidentId, List<AgentFinding> findings) {
        cache.put(incidentId, findings);
    }

    public List<AgentFinding> get(UUID incidentId) {
        return cache.getOrDefault(incidentId, List.of());
    }
}
