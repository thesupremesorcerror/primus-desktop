package com.primus.core.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IncidentMemoryRepository extends JpaRepository<IncidentMemory, UUID> {
}