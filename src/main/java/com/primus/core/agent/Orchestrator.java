package com.primus.core.agent;

import com.primus.core.incident.Incident;
import com.primus.core.remediation.RemediationCandidateService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class Orchestrator {

    private final LogAgent logAgent;
    private final MetricsAgent metricsAgent;
    private final RagAgent ragAgent;
    private final DiagnosisAgent diagnosisAgent;
    private final RemediationAgent remediationAgent;
    private final DiagnosticTools diagnosticTools;
    private final PolicyEngine policyEngine;
    private final RemediationCandidateService candidateService;

    public Orchestrator(LogAgent logAgent, MetricsAgent metricsAgent, RagAgent ragAgent,
                        DiagnosisAgent diagnosisAgent, RemediationAgent remediationAgent,
                        DiagnosticTools diagnosticTools, PolicyEngine policyEngine,
                        RemediationCandidateService candidateService) {
        this.logAgent = logAgent;
        this.metricsAgent = metricsAgent;
        this.ragAgent = ragAgent;
        this.diagnosisAgent = diagnosisAgent;
        this.remediationAgent = remediationAgent;
        this.diagnosticTools = diagnosticTools;
        this.policyEngine = policyEngine;
        this.candidateService = candidateService;
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
        OrchestratorResult result = new OrchestratorResult();
        result.incidentId = incident.getId();

        // Stage 1: parallel-ish investigation (sequential calls, independent findings)
        AgentFinding logFinding = logAgent.investigate(incident, null);
        AgentFinding metricsFinding = metricsAgent.investigate(incident, null);

        String earlyContext = "Log findings: %s | Metrics findings: %s"
                .formatted(logFinding.summary(), metricsFinding.summary());
        AgentFinding ragFinding = ragAgent.investigate(incident, earlyContext);

        List<AgentFinding> findings = List.of(logFinding, metricsFinding, ragFinding);
        result.findings = findings;

        // Stage 2: synthesis
        AgentFinding diagnosis = diagnosisAgent.synthesize(incident, findings);
        result.diagnosis = diagnosis.summary();

        // Stage 3: remediation proposal + policy gating (mirrors AgentLoop's Stage 5/7 logic)
        RemediationAgent.Proposal proposal = remediationAgent.propose(incident, diagnosis);

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

    private Object executeRemediation(String toolName, String serviceName) {
        return switch (toolName) {
            case "restart_service" -> diagnosticTools.restartService(serviceName);
            case "rollback_deployment" -> diagnosticTools.rollbackDeployment(serviceName);
            case "scale_service" -> diagnosticTools.scaleService(serviceName);
            default -> Map.of("error", "Unknown remediation tool: " + toolName);
        };
    }
}