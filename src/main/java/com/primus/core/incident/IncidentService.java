package com.primus.core.incident;

import com.primus.core.agent.AgentFinding;
import com.primus.core.agent.DiagnosticTools;
import com.primus.core.agent.FindingsCache;
import com.primus.core.agent.Orchestrator;
import com.primus.core.diagnosis.DiagnosisService;
import com.primus.core.memory.IncidentMemoryService;
import com.primus.core.remediation.RemediationCandidate;
import com.primus.core.remediation.RemediationCandidateRepository;
import com.primus.core.remediation.RemediationCandidateService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Old:    JavaFX/React -> HTTP -> IncidentController -> (logic was here)
 * New:    JavaFX IncidentView -> IncidentService.investigateIncident(id) -> Orchestrator
 *
 * Every method here is a line-for-line port of the corresponding
 * IncidentController handler -- same logic, same order of operations, same
 * @PreAuthorize rules. Only the HTTP annotations (@GetMapping etc.),
 * @PathVariable/@RequestBody, and the REST-specific request/response
 * wrapping were removed.
 */
@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final DiagnosisService diagnosisService;
    private final Orchestrator orchestrator;
    private final IncidentMemoryService incidentMemoryService;
    private final RemediationCandidateService candidateService;
    private final RemediationCandidateRepository remediationCandidateRepository;
    private final DiagnosticTools diagnosticTools;
    private final FindingsCache findingsCache;

    public IncidentService(IncidentRepository incidentRepository,
                           DiagnosisService diagnosisService,
                           Orchestrator orchestrator,
                           IncidentMemoryService incidentMemoryService,
                           RemediationCandidateService candidateService,
                           RemediationCandidateRepository remediationCandidateRepository,
                           DiagnosticTools diagnosticTools,
                           FindingsCache findingsCache) {
        this.incidentRepository = incidentRepository;
        this.diagnosisService = diagnosisService;
        this.orchestrator = orchestrator;
        this.incidentMemoryService = incidentMemoryService;
        this.candidateService = candidateService;
        this.remediationCandidateRepository = remediationCandidateRepository;
        this.diagnosticTools = diagnosticTools;
        this.findingsCache = findingsCache;
    }

    public List<Incident> getAllIncidents() {
        return incidentRepository.findAll();
    }

    public Incident getIncident(UUID id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Incident not found: " + id));
    }

    public Incident diagnoseIncident(UUID id) {
        return diagnosisService.diagnose(id);
    }

    public Incident createIncident(String title, String severity, String affectedService) {
        Incident incident = new Incident(title, severity, "OPEN", affectedService);
        return incidentRepository.save(incident);
    }

    public Incident investigateIncident(UUID id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Incident not found: " + id));

        Orchestrator.OrchestratorResult result = orchestrator.run(incident);

        findingsCache.store(incident.getId(), result.findings);

        incident.setRootCauseGuess(result.diagnosis);

        if (result.approvalRequired) {
            incident.setStatus("AWAITING_APPROVAL");
        } else if (result.actionTaken != null && result.verified) {
            incident.setStatus("RESOLVED");
            incident.setResolvedAt(Instant.now());
            incidentMemoryService.storeResolvedIncident(incident, result.actionTaken);
        } else if (result.actionTaken != null) {
            incident.setStatus("REOPENED");
        } else {
            incident.setStatus("DIAGNOSING");
        }

        return incidentRepository.save(incident);
    }

    public List<RemediationCandidate> getCandidates(UUID id) {
        return candidateService.getCandidatesForIncident(id);
    }

    /**
     * Live snapshot of metrics and recent logs for the incident's affected
     * service, using the same DiagnosticTools the agents themselves call
     * during investigation. Lets the UI show what evidence the agents are
     * working from, without inventing or caching separate data.
     */
    public Map<String, Object> getDiagnostics(UUID id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Incident not found: " + id));

        String serviceName = incident.getAffectedService();
        Map<String, Object> metrics = diagnosticTools.getMetrics(serviceName);
        Map<String, Object> logsResult = diagnosticTools.getLogs(serviceName);

        return Map.of(
                "serviceName", serviceName,
                "metrics", metrics,
                "logs", logsResult.get("recentLogs")
        );
    }

    /**
     * Deletes all incidents created by eval runs (title starts with "[EVAL]").
     * Admin-only housekeeping -- lets the dashboard stay clean for demos
     * after running the eval harness, without touching manually-created or
     * fault-simulator-detected incidents.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Map<String, Object> cleanupEvalIncidents() {
        List<Incident> toDelete = incidentRepository.findAll().stream()
                .filter(i -> i.getTitle().startsWith("[EVAL]"))
                .toList();
        // Remediation candidates reference incidents via a foreign key with
        // no cascade -- they have to go first, or Postgres rejects the
        // incident delete. This was a pre-existing bug in the original
        // controller logic (not something the desktop migration introduced),
        // surfaced now because it's the first time this button was actually
        // clicked against real data.
        for (Incident incident : toDelete) {
            remediationCandidateRepository.deleteByIncidentId(incident.getId());
        }
        incidentRepository.deleteAll(toDelete);
        return Map.of("deletedCount", toDelete.size());
    }

    /**
     * Evidence each specialist agent (LogAgent, MetricsAgent, RagAgent)
     * gathered during the incident's most recent investigation -- real
     * AgentFinding data from that run, not re-derived or invented. Empty if
     * the incident hasn't been investigated yet in this app session.
     */
    public List<AgentFinding> getEvidence(UUID id) {
        return findingsCache.get(id);
    }

    @PreAuthorize("hasAnyRole('APPROVER', 'ADMIN')")
    public Incident approveCandidate(UUID id, UUID candidateId) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Incident not found: " + id));
        RemediationCandidate candidate = candidateService.getCandidate(candidateId);

        Object toolResult = switch (candidate.getActionType()) {
            case "restart_service" -> diagnosticTools.restartService(candidate.getServiceName());
            case "rollback_deployment" -> diagnosticTools.rollbackDeployment(candidate.getServiceName());
            case "scale_service" -> diagnosticTools.scaleService(candidate.getServiceName());
            default -> throw new RuntimeException("Unknown action type: " + candidate.getActionType());
        };

        candidateService.updateStatus(candidateId, "EXECUTED");

        boolean healthy = diagnosticTools.checkServiceHealth(candidate.getServiceName());
        String actionTaken = candidate.getActionType() + "(" + candidate.getServiceName() + ")";

        if (healthy) {
            incident.setStatus("RESOLVED");
            incident.setResolvedAt(Instant.now());
            incidentMemoryService.storeResolvedIncident(incident, actionTaken);
        } else {
            incident.setStatus("REOPENED");
        }

        return incidentRepository.save(incident);
    }

    @PreAuthorize("hasAnyRole('APPROVER', 'ADMIN')")
    public Incident rejectCandidate(UUID id, UUID candidateId) {
        candidateService.updateStatus(candidateId, "REJECTED");

        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Incident not found: " + id));
        incident.setStatus("DIAGNOSING");
        return incidentRepository.save(incident);
    }
}