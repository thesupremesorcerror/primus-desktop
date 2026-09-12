package com.primus.core.memory;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_memory")
public class IncidentMemory {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "incident_id")
    private UUID incidentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "remediation_taken", columnDefinition = "TEXT")
    private String remediationTaken;

    @Column(columnDefinition = "TEXT")
    private String outcome;

    @Column(columnDefinition = "TEXT")
    private String embedding;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IncidentMemory() {
    }

    public IncidentMemory(UUID incidentId, String summary, String rootCause, String remediationTaken, String outcome) {
        this.incidentId = incidentId;
        this.summary = summary;
        this.rootCause = rootCause;
        this.remediationTaken = remediationTaken;
        this.outcome = outcome;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getIncidentId() { return incidentId; }
    public String getSummary() { return summary; }
    public String getRootCause() { return rootCause; }
    public String getRemediationTaken() { return remediationTaken; }
    public String getOutcome() { return outcome; }
    public String getEmbedding() { return embedding; }
    public void setEmbedding(String embedding) { this.embedding = embedding; }
    public Instant getCreatedAt() { return createdAt; }
}