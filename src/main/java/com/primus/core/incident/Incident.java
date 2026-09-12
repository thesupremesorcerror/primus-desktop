package com.primus.core.incident;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(nullable = false)
    private String status; // OPEN, DIAGNOSING, AWAITING_APPROVAL, REMEDIATING, VERIFYING, RESOLVED, REOPENED

    @Column(name = "affected_service", nullable = false)
    private String affectedService;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "root_cause_guess")
    private String rootCauseGuess;

    protected Incident() {
        // required by JPA
    }

    public Incident(String title, String severity, String status, String affectedService) {
        this.title = title;
        this.severity = severity;
        this.status = status;
        this.affectedService = affectedService;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // Getters and setters

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAffectedService() {
        return affectedService;
    }

    public void setAffectedService(String affectedService) {
        this.affectedService = affectedService;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getRootCauseGuess() {
        return rootCauseGuess;
    }

    public void setRootCauseGuess(String rootCauseGuess) {
        this.rootCauseGuess = rootCauseGuess;
    }
}