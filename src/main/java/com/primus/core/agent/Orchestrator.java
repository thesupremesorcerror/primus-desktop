package com.primus.core.agent;

import com.primus.core.incident.Incident;
import com.primus.core.remediation.RemediationCandidateService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class Orchestrator {

    private static final List<String> AGENT_NAMES =
            List.of("LogAgent", "MetricsAgent", "RagAgent", "DiagnosisAgent", "RemediationAgent");

    private final LogAgent logAgent;
    private final MetricsAgent metricsAgent;
    private final RagAgent ragAgent;
    private final DiagnosisAgent diagnosisAgent;
    private final RemediationAgent remediationAgent;
    private final DiagnosticTools diagnosticTools;
    private final PolicyEngine policyEngine;
    private final RemediationCandidateService candidateService;
    private final AgentStatusTracker statusTracker;

    public Orchestrator(LogAgent logAgent, MetricsAgent metricsAgent, RagAgent ragAgent,
                        DiagnosisAgent diagnosisAgent, RemediationAgent remediationAgent,
                        DiagnosticTools diagnosticTools, PolicyEngine policyEngine,
                        RemediationCandidateService candidateService, AgentStatusTracker statusTracker) {
        this.logAgent = logAgent;
        this.metricsAgent = metricsAgent;
        this.ragAgent = ragAgent;
        this.diagnosisAgent = diagnosisAgent;
        this.remediationAgent = remediationAgent;
        this.diagnosticTools = diagnosticTools;
        this.policyEngine = policyEngine;
        this.candidateService = candidateService;
        this.statusTracker = statusTracker;
    }

    public static class OrchestratorResult {
        public UUID incidentId;
        public String diagnosis;
        public List<AgentFinding> findings;
        public String actionTaken;
        public String actionRiskLevel;
        public boolean verified;
        public boolean approvalRequired;
    }

    public OrchestratorResult run(Incident incident) {
        UUID incidentId = incident.getId();
        statusTracker.reset(incidentId, AGENT_NAMES);

        OrchestratorResult result = new OrchestratorResult();
        result.incidentId = incidentId;

        // Stage 1: parallel-ish investigation (sequential calls, independent findings)
        AgentFinding logFinding = runStage(incidentId, "LogAgent", () -> logAgent.investigate(incident, null));
        AgentFinding metricsFinding = runStage(incidentId, "MetricsAgent", () -> metricsAgent.investigate(incident, null));

        String earlyContext = "Log findings: %s | Metrics findings: %s"
                .formatted(logFinding.summary(), metricsFinding.summary());
        AgentFinding ragFinding = runStage(incidentId, "RagAgent", () -> ragAgent.investigate(incident, earlyContext));

        List<AgentFinding> findings = List.of(logFinding, metricsFinding, ragFinding);
        result.findings = findings;

        // Stage 2: synthesis
        AgentFinding diagnosis = runStage(incidentId, "DiagnosisAgent", () -> diagnosisAgent.synthesize(incident, findings));
        result.diagnosis = diagnosis.summary();

        // Stage 3: remediation proposal + policy gating (mirrors AgentLoop's Stage 5/7 logic)
        RemediationAgent.Proposal proposal = runStage(incidentId, "RemediationAgent", () -> remediationAgent.propose(incident, diagnosis));

        if (proposal.hasProposal()) {
            String toolName = proposal.toolName();
            String serviceName = proposal.serviceName();
            String risk = policyEngine.getRiskLevel(toolName);

            if (!policyEngine.isAutoExecutionAllowed(toolName)) {
                result.approvalRequired = true;
                result.actionRiskLevel = risk;
                candidateService.generateCandidates(incident.getId(), serviceName, toolName);
            } else {
                Object toolResult = executeRemediation(toolName, serviceName);
                result.actionTaken = toolName + "(" + serviceName + ")";
                result.actionRiskLevel = risk;
                result.verified = diagnosticTools.checkServiceHealth(serviceName);
            }
        }

        return result;
    }

    /**
     * Wraps one agent's stage with real status tracking: RUNNING before the
     * call, COMPLETED after it succeeds, FAILED (then rethrown) if it
     * throws. This is what makes the UI's live status panel real rather
     * than simulated -- each transition happens exactly when the
     * corresponding agent call actually starts/finishes.
     */
    private <T> T runStage(UUID incidentId, String agentName, Supplier<T> action) {
        statusTracker.setStatus(incidentId, agentName, AgentStatusTracker.Status.RUNNING);
        try {
            T value = action.get();
            statusTracker.setStatus(incidentId, agentName, AgentStatusTracker.Status.COMPLETED);
            return value;
        } catch (RuntimeException e) {
            statusTracker.setStatus(incidentId, agentName, AgentStatusTracker.Status.FAILED);
            throw e;
        }
    }

    private Object executeRemediation(String toolName, String serviceName) {
        return switch (toolName) {
            case "restart_service" -> diagnosticTools.restartService(serviceName);
            case "rollback_deployment" -> diagnosticTools.rollbackDeployment(serviceName);
            case "scale_service" -> diagnosticTools.scaleService(serviceName);
            default -> Map.of("error", "Unknown remediation tool: " + toolName);
        };
    }
}