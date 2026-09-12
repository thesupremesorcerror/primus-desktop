package com.primus.core.agent;

import com.primus.core.diagnosis.GeminiClient;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class RagAgent extends AbstractSpecialistAgent {

    public RagAgent(GeminiClient geminiClient, DiagnosticTools diagnosticTools) {
        super(geminiClient, diagnosticTools);
    }

    @Override public String name() { return "rag_agent"; }

    @Override protected Set<String> allowedTools() {
        return Set.of("search_runbooks", "search_incident_memory");
    }

    @Override protected String systemPromptTemplate() {
        return """
            You are the RAG Agent. Use search_runbooks and search_incident_memory to find
            relevant past guidance, informed by what other agents have already found.
            Respond ONLY with JSON, no other text, matching:
            {"confidence": 0.0-1.0, "summary": "one-line takeaway", "evidence": [{"type":"runbook_match","detail":"...","source":"search_runbooks"}]}

            Incident: %s | Severity: %s | Service: %s | Prior findings: %s
            """;
    }
}