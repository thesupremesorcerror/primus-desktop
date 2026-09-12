package com.primus.core.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real live status per agent per incident, updated by Orchestrator as it
 * progresses through LogAgent -> MetricsAgent -> RagAgent -> DiagnosisAgent
 * -> RemediationAgent. The JavaFX UI polls getStatuses(incidentId) on a
 * timer while an investigation runs (on a background thread) to show a
 * genuinely live status panel, instead of only the final results.
 *
 * Keyed by incidentId (not global) since in principle more than one
 * incident's status could be tracked, though this desktop app only ever
 * runs one investigation at a time in practice.
 */
@Component
public class AgentStatusTracker {

    public enum Status { WAITING, RUNNING, COMPLETED, FAILED }

    private final Map<UUID, Map<String, Status>> statusByIncident = new ConcurrentHashMap<>();

    public void reset(UUID incidentId, List<String> agentNames) {
        Map<String, Status> statuses = new ConcurrentHashMap<>();
        for (String name : agentNames) {
            statuses.put(name, Status.WAITING);
        }
        statusByIncident.put(incidentId, statuses);
    }

    public void setStatus(UUID incidentId, String agentName, Status status) {
        statusByIncident.computeIfAbsent(incidentId, k -> new ConcurrentHashMap<>()).put(agentName, status);
    }

    /** Returns an empty map if no investigation has run for this incident yet. */
    public Map<String, Status> getStatuses(UUID incidentId) {
        return statusByIncident.getOrDefault(incidentId, Map.of());
    }

    public void clear(UUID incidentId) {
        statusByIncident.remove(incidentId);
    }
}