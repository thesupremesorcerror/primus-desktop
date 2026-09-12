package com.primus.core.remediation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RemediationCandidateRepository extends JpaRepository<RemediationCandidate, UUID> {
    List<RemediationCandidate> findByIncidentId(UUID incidentId);
    void deleteByIncidentId(UUID incidentId);
}