package com.primus.core.agent;

import com.primus.core.diagnosis.GeminiClient;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class LogAgent extends AbstractSpecialistAgent {

    public LogAgent(GeminiClient geminiClient, DiagnosticTools diagnosticTools) {
        super(geminiClient, diagnosticTools);
    }

    @Override public String name() { return "log_agent"; }

    @Override protected Set<String> allowedTools() { return Set.of("get_logs"); }

    @Override protected String systemPromptTemplate() {
        return """
            You are the Log Agent in an incident-response system. Call get_logs and analyze
            the results. Respond ONLY with JSON, no other text, matching:
            {"confidence": 0.0-1.0, "summary": "one-line takeaway", "evidence": [{"type":"log_pattern","detail":"...","source":"get_logs"}]}

            Incident: %s | Severity: %s | Service: %s | Prior context: %s
            """;
    }
}