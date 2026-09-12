package com.primus.core.knowledge;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kb_documents")
public class KbDocument {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "doc_type", nullable = false)
    private String docType; // runbook, postmortem, arch_doc, troubleshooting_guide

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String embedding; // JSON array of floats, stored as a string

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected KbDocument() {
    }

    public KbDocument(String docType, String title, String content) {
        this.docType = docType;
        this.title = title;
        this.content = content;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getDocType() {
        return docType;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
