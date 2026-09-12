package com.primus.core.agent;

import com.primus.core.incident.Incident;
import com.primus.core.remediation.RemediationCandidateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestratorTest {

    @Mock private LogAgent logAgent;
    @Mock private MetricsAgent metricsAgent;
    @Mock private RagAgent ragAgent;
    @Mock private DiagnosisAgent diagnosisAgent;
    @Mock private RemediationAgent remediationAgent;
    @Mock private DiagnosticTools diagnosticTools;
    @Mock private PolicyEngine policyEngine;
    @Mock private RemediationCandidateService candidateService;

    private Orchestrator orchestrator;
    private Incident incident;

    @BeforeEach
    void setUp() {
        orchestrator = new Orchestrator(logAgent, metricsAgent, ragAgent, diagnosisAgent,
                remediationAgent, diagnosticTools, policyEngine, candidateService);

        incident = new Incident("auth-api is not responding", "HIGH", "OPEN", "auth-api");

        AgentFinding logFinding = new AgentFinding("log_agent", 0.8, "deployment-related errors", List.of(), List.of("get_logs"));
        AgentFinding metricsFinding = new AgentFinding("metrics_agent", 0.7, "elevated error rate", List.of(), List.of("get_metrics"));
        AgentFinding ragFinding = new AgentFinding("rag_agent", 0.6, "matches deployment runbook", List.of(), List.of("search_runbooks"));
        AgentFinding diagnosis = new AgentFinding("diagnosis_agent", 0.9, "recent deployment caused this", List.of(), List.of());

        when(logAgent.investigate(eq(incident), any())).thenReturn(logFinding);
        when(metricsAgent.investigate(eq(incident), any())).thenReturn(metricsFinding);
        when(ragAgent.investigate(eq(incident), anyString())).thenReturn(ragFinding);
        when(diagnosisAgent.synthesize(eq(incident), anyList())).thenReturn(diagnosis);
    }

    @Test
    void highRiskProposalRequiresApprovalAndDoesNotExecute() {
        RemediationAgent.Proposal proposal = new RemediationAgent.Proposal(true, "rollback_deployment", "auth-api", "deployment caused it");
        when(remediationAgent.propose(eq(incident), any())).thenReturn(proposal);
        when(policyEngine.getRiskLevel("rollback_deployment")).thenReturn("HIGH");
        when(policyEngine.isAutoExecutionAllowed("rollback_deployment")).thenReturn(false);

        Orchestrator.OrchestratorResult result = orchestrator.run(incident);

        assertTrue(result.approvalRequired);
        assertEquals("HIGH", result.actionRiskLevel);
        assertNull(result.actionTaken);
        assertFalse(result.verified);
        verify(candidateService).generateCandidates(incident.getId(), "auth-api", "rollback_deployment");
        verify(diagnosticTools, never()).rollbackDeployment(anyString());
    }

    @Test
    void mediumRiskProposalAutoExecutesAndVerifies() {
        RemediationAgent.Proposal proposal = new RemediationAgent.Proposal(true, "restart_service", "auth-api", "pool exhaustion");
        when(remediationAgent.propose(eq(incident), any())).thenReturn(proposal);
        when(policyEngine.getRiskLevel("restart_service")).thenReturn("MEDIUM");
        when(policyEngine.isAutoExecutionAllowed("restart_service")).thenReturn(true);
        when(diagnosticTools.restartService("auth-api")).thenReturn(Map.of("status", "restarted"));
        when(diagnosticTools.checkServiceHealth("auth-api")).thenReturn(true);

        Orchestrator.OrchestratorResult result = orchestrator.run(incident);

        assertFalse(result.approvalRequired);
        assertEquals("restart_service(auth-api)", result.actionTaken);
        assertEquals("MEDIUM", result.actionRiskLevel);
        assertTrue(result.verified);
        verify(candidateService, never()).generateCandidates(any(), anyString(), anyString());
    }

    @Test
    void noProposalMeansNoActionAndNoApproval() {
        RemediationAgent.Proposal proposal = new RemediationAgent.Proposal(false, null, null, "no clear action");
        when(remediationAgent.propose(eq(incident), any())).thenReturn(proposal);

        Orchestrator.OrchestratorResult result = orchestrator.run(incident);

        assertFalse(result.approvalRequired);
        assertNull(result.actionTaken);
        assertFalse(result.verified);
        verifyNoInteractions(candidateService);
        verifyNoInteractions(diagnosticTools);
    }

    @Test
    void findingsFromAllThreeSpecialistsArePassedToDiagnosis() {
        RemediationAgent.Proposal proposal = new RemediationAgent.Proposal(false, null, null, "none");
        when(remediationAgent.propose(eq(incident), any())).thenReturn(proposal);

        orchestrator.run(incident);

        verify(diagnosisAgent).synthesize(eq(incident), argThat(findings ->
                findings.size() == 3
                        && findings.stream().anyMatch(f -> f.agent().equals("log_agent"))
                        && findings.stream().anyMatch(f -> f.agent().equals("metrics_agent"))
                        && findings.stream().anyMatch(f -> f.agent().equals("rag_agent"))
        ));
    }
}