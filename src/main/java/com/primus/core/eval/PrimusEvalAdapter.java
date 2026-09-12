package com.primus.core.eval;

import com.primus.core.agent.AgentLoop;
import com.primus.core.agent.DiagnosticTools;
import com.primus.core.agent.Orchestrator;
import com.primus.core.diagnosis.GeminiCallCounter;
import com.primus.core.incident.Incident;
import com.primus.core.observability.FaultType;
import com.primus.core.remediation.RemediationCandidate;
import com.primus.core.remediation.RemediationCandidateService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class PrimusEvalAdapter {

    private final AgentLoop agentLoop;
    private final Orchestrator orchestrator;
    private final DiagnosticTools diagnosticTools;
    private final RemediationCandidateService candidateService;
    private final GeminiCallCounter callCounter;

    public PrimusEvalAdapter(AgentLoop agentLoop, Orchestrator orchestrator,
                             DiagnosticTools diagnosticTools, RemediationCandidateService candidateService,
                             GeminiCallCounter callCounter) {
        this.agentLoop = agentLoop;
        this.orchestrator = orchestrator;
        this.diagnosticTools = diagnosticTools;
        this.candidateService = candidateService;
        this.callCounter = callCounter;
    }

    public EvalResult runSingleAgent(Incident incident, FaultType groundTruth) {
        callCounter.getAndReset(); // clear any leftover count before this run
        AgentLoop.AgentResult result = agentLoop.diagnose(incident);
        String proposedAction = extractActionName(result.actionTaken);
        boolean resolved = result.verified;

        if (proposedAction == null && result.approvalRequired) {
            RemediationCandidate recommended = recommendedCandidate(incident.getId());
            if (recommended != null) {
                proposedAction = recommended.getActionType();
                resolved = executeAndVerify(recommended.getActionType(), recommended.getServiceName());
            } else {
                proposedAction = "none";
                resolved = false;
            }
        }

        int llmCalls = callCounter.getAndReset();
        return new EvalResult("single_agent_primus", incident.getAffectedService(),
                scenarioLabel(groundTruth), result.diagnosis, proposedAction == null ? "none" : proposedAction,
                resolved, llmCalls, 0L);
    }

    public EvalResult runMultiAgent(Incident incident, FaultType groundTruth) {
        callCounter.getAndReset(); // clear any leftover count before this run
        Orchestrator.OrchestratorResult result = orchestrator.run(incident);
        String proposedAction = extractActionName(result.actionTaken);
        boolean resolved = result.verified;

        if (proposedAction == null && result.approvalRequired) {
            RemediationCandidate recommended = recommendedCandidate(incident.getId());
            if (recommended != null) {
                proposedAction = recommended.getActionType();
                resolved = executeAndVerify(recommended.getActionType(), recommended.getServiceName());
            } else {
                proposedAction = "none";
                resolved = false;
            }
        }

        int llmCalls = callCounter.getAndReset();
        return new EvalResult("multi_agent_primus", incident.getAffectedService(),
                scenarioLabel(groundTruth), result.diagnosis, proposedAction == null ? "none" : proposedAction,
                resolved, llmCalls, 0L);
    }

    private String extractActionName(String actionTaken) {
        if (actionTaken == null) return null;
        int parenIdx = actionTaken.indexOf('(');
        return parenIdx == -1 ? actionTaken : actionTaken.substring(0, parenIdx);
    }

    private RemediationCandidate recommendedCandidate(UUID incidentId) {
        List<RemediationCandidate> candidates = candidateService.getCandidatesForIncident(incidentId);
        return candidates.stream()
                .filter(c -> Boolean.TRUE.equals(c.getRecommended()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Executes a remediation action and checks the resulting health, exactly as
     * IncidentController.approveCandidate() does for a human-approved action.
     * Used only within the eval harness to auto-approve HIGH-risk candidates so
     * we can measure whether the underlying diagnosis+remediation decision was
     * correct, independent of the human-approval gate (which is a deliberate,
     * separate safety control and not itself part of decision quality).
     */
    private boolean executeAndVerify(String actionType, String serviceName) {
        switch (actionType) {
            case "restart_service" -> diagnosticTools.restartService(serviceName);
            case "rollback_deployment" -> diagnosticTools.rollbackDeployment(serviceName);
            case "scale_service" -> diagnosticTools.scaleService(serviceName);
            default -> { /* unknown action type: leave service state unchanged */ }
        }
        return diagnosticTools.checkServiceHealth(serviceName);
    }

    private String scenarioLabel(FaultType faultType) {
        return switch (faultType) {
            case DEPLOYMENT_ISSUE -> "BAD_DEPLOYMENT";
            case CONNECTION_POOL_EXHAUSTION -> "CONNECTION_POOL";
            case TRANSIENT_GLITCH -> "TRANSIENT_GLITCH";
            case NONE -> "UNKNOWN";
        };
    }
}