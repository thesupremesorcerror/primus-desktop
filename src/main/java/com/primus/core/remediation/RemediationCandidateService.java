package com.primus.core.remediation;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RemediationCandidateService {

    private final RemediationCandidateRepository repository;

    public RemediationCandidateService(RemediationCandidateRepository repository) {
        this.repository = repository;
    }

    /**
     * Generates candidate remediation actions with heuristic risk/recovery scores.
     * This does NOT need to be a sophisticated ML model yet (per the architecture doc) --
     * the point is the architecture: multiple candidates are considered and compared
     * before one is selected, rather than blindly executing the first idea.
     */
    public List<RemediationCandidate> generateCandidates(UUID incidentId, String serviceName, String recommendedAction) {
        List<RemediationCandidate> candidates = List.of(
                new RemediationCandidate(incidentId, "restart_service", serviceName, "MEDIUM", 75.0,
                        "Clears transient state (connections, memory) without changing code or config. Fast, low blast radius.",
                        "restart_service".equals(recommendedAction)),
                new RemediationCandidate(incidentId, "scale_service", serviceName, "MEDIUM", 65.0,
                        "Adds capacity to absorb load, but does not address an underlying leak or bug.",
                        "scale_service".equals(recommendedAction)),
                new RemediationCandidate(incidentId, "rollback_deployment", serviceName, "HIGH", 90.0,
                        "Highest expected recovery if a recent deployment is the root cause, but reverts any changes in that release.",
                        "rollback_deployment".equals(recommendedAction))
        );

        return repository.saveAll(candidates);
    }

    public List<RemediationCandidate> getCandidatesForIncident(UUID incidentId) {
        return repository.findByIncidentId(incidentId);
    }

    public RemediationCandidate getCandidate(UUID candidateId) {
        return repository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found: " + candidateId));
    }
    public RemediationCandidate updateStatus(UUID candidateId, String status) {
        RemediationCandidate candidate = getCandidate(candidateId);
        candidate.setStatus(status);
        return repository.save(candidate);
    }
}