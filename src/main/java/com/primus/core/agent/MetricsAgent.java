package com.primus.core.agent;

import com.primus.core.diagnosis.GeminiClient;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class MetricsAgent extends AbstractSpecialistAgent {

    public MetricsAgent(GeminiClient geminiClient, DiagnosticTools diagnosticTools) {
        super(geminiClient, diagnosticTools);
    }

    @Override public String name() { return "metrics_agent"; }

    @Override protected Set<String> allowedTools() { return Set.of("get_metrics"); }

    @Override protected String systemPromptTemplate() {
        return """
            You are the Metrics Agent. Call get_metrics and analyze CPU/memory/error-rate/
            pool-utilization. Respond ONLY with JSON, no other text, matching:
            {"confidence": 0.0-1.0, "summary": "one-line takeaway", "evidence": [{"type":"metric_anomaly","detail":"...","source":"get_metrics"}]}

            Incident: %s | Severity: %s | Service: %s | Prior context: %s
            """;
    }
}
