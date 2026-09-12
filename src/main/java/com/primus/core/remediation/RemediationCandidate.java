package com.primus.core.remediation;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "remediation_candidates")
public class RemediationCandidate {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "risk_level", nullable = false)
    private String riskLevel;

    @Column(name = "expected_recovery_pct")
    private Double expectedRecoveryPct;

    @Column(columnDefinition = "TEXT")
    private String rationale;

    private Boolean recommended = false;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RemediationCandidate() {
    }

    public RemediationCandidate(UUID incidentId, String actionType, String serviceName, String riskLevel, Double expectedRecoveryPct, String rationale, Boolean recommended) {
        this.incidentId = incidentId;
        this.actionType = actionType;
        this.serviceName = serviceName;
        this.riskLevel = riskLevel;
        this.expectedRecoveryPct = expectedRecoveryPct;
        this.rationale = rationale;
        this.recommended = recommended;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getIncidentId() { return incidentId; }
    public String getActionType() { return actionType; }
    public String getServiceName() { return serviceName; }
    public String getRiskLevel() { return riskLevel; }
    public Double getExpectedRecoveryPct() { return expectedRecoveryPct; }
    public String getRationale() { return rationale; }
    public Boolean getRecommended() { return recommended; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}