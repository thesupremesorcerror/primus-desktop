package com.primus.core.agent;

import com.primus.core.incident.Incident;

public interface SpecialistAgent {
    String name();
    AgentFinding investigate(Incident incident, String context);
}