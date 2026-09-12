package com.primus.core.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface KbDocumentRepository extends JpaRepository<KbDocument, UUID> {
}