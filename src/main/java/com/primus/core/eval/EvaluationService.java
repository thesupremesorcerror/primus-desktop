package com.primus.core.eval;

import com.primus.core.incident.Incident;
import com.primus.core.incident.IncidentRepository;
import com.primus.core.observability.FaultSimulator;
import com.primus.core.observability.FaultType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

@Service
public class EvaluationService {

    private static final List<String> SERVICES =
            List.of("auth-api", "checkout-api", "payment-api", "orders-api");
    private static final int RUNS_PER_SYSTEM = 10;

    private final IncidentRepository incidentRepository;
    private final FaultSimulator faultSimulator;
    private final RuleBasedDiagnoser ruleBasedDiagnoser;
    private final LlmNoRagDiagnoser llmNoRagDiagnoser;
    private final LlmRagDiagnoser llmRagDiagnoser;
    private final PrimusEvalAdapter primusEvalAdapter;

    public EvaluationService(IncidentRepository incidentRepository,
                             FaultSimulator faultSimulator,
                             RuleBasedDiagnoser ruleBasedDiagnoser,
                             LlmNoRagDiagnoser llmNoRagDiagnoser,
                             LlmRagDiagnoser llmRagDiagnoser,
                             PrimusEvalAdapter primusEvalAdapter) {
        this.incidentRepository = incidentRepository;
        this.faultSimulator = faultSimulator;
        this.ruleBasedDiagnoser = ruleBasedDiagnoser;
        this.llmNoRagDiagnoser = llmNoRagDiagnoser;
        this.llmRagDiagnoser = llmRagDiagnoser;
        this.primusEvalAdapter = primusEvalAdapter;
    }

    public List<EvalSummary> runFullEvaluation() {
        List<EvalSummary> summaries = new ArrayList<>();

        summaries.add(runSystem("rule_based", (incident, fault) -> runRuleBased(incident)));
        summaries.add(runSystem("llm_no_rag", (incident, fault) -> runLlmNoRag(incident)));
        summaries.add(runSystem("llm_rag", (incident, fault) -> runLlmRag(incident)));
        summaries.add(runSystem("single_agent_primus", primusEvalAdapter::runSingleAgent));
        summaries.add(runSystem("multi_agent_primus", primusEvalAdapter::runMultiAgent));

        return summaries;
    }

    private EvalSummary runSystem(String name, BiFunction<Incident, FaultType, EvalResult> runner) {
        List<EvalResult> results = new ArrayList<>();
        for (int i = 0; i < RUNS_PER_SYSTEM; i++) {
            String service = SERVICES.get(i % SERVICES.size());

            // Inject a real, known fault BEFORE running the diagnoser, and capture
            // it as ground truth up front — the agent's own remediation may heal
            // the service mid-run, so querying state afterward would be wrong.
            FaultType groundTruth = faultSimulator.kill(service);

            Incident incident = createEvalIncident(name, service);
            long start = System.currentTimeMillis();
            EvalResult result = runWithRetry(runner, incident, groundTruth, name, service);
            long elapsed = System.currentTimeMillis() - start;
            results.add(withLatency(result, elapsed));

            faultSimulator.heal(service); // reset so the next run starts clean
        }
        return EvalSummary.from(name, results);
    }

    private EvalResult runWithRetry(BiFunction<Incident, FaultType, EvalResult> runner,
                                    Incident incident, FaultType groundTruth,
                                    String systemName, String serviceName) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return runner.apply(incident, groundTruth);
            } catch (Exception e) {
                System.out.println("[EVAL] " + systemName + " attempt " + attempt + " failed for "
                        + serviceName + ": " + e.getMessage());
                if (attempt < 2) {
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                }
            }
        }
        return new EvalResult(systemName, serviceName, "UNKNOWN",
                "FAILED: exception during eval run", "none", false, 0, 0L);
    }

    private EvalResult withLatency(EvalResult r, long latencyMs) {
        return new EvalResult(r.systemName(), r.serviceName(), r.observedScenario(), r.diagnosis(),
                r.proposedAction(), r.resolved(), r.llmCallCount(), latencyMs);
    }

    private Incident createEvalIncident(String systemName, String serviceName) {
        Incident incident = new Incident(
                "[EVAL] " + serviceName + " is not responding",
                "HIGH",
                "OPEN",
                serviceName
        );
        return incidentRepository.save(incident);
    }

    private EvalResult runRuleBased(Incident incident) {
        return ruleBasedDiagnoser.diagnose(incident.getAffectedService());
    }

    private EvalResult runLlmNoRag(Incident incident) {
        return llmNoRagDiagnoser.diagnose(incident.getTitle(), incident.getSeverity(), incident.getAffectedService());
    }

    private EvalResult runLlmRag(Incident incident) {
        return llmRagDiagnoser.diagnose(incident.getTitle(), incident.getSeverity(), incident.getAffectedService());
    }
}