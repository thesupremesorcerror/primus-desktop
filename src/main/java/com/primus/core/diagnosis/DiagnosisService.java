package com.primus.core.diagnosis;

import com.primus.core.incident.Incident;
import com.primus.core.incident.IncidentRepository;
import com.primus.core.knowledge.KbDocument;
import com.primus.core.knowledge.RetrievalService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DiagnosisService {

    private final IncidentRepository incidentRepository;
    private final GeminiClient geminiClient;
    private final RetrievalService retrievalService;

    public DiagnosisService(IncidentRepository incidentRepository, GeminiClient geminiClient, RetrievalService retrievalService) {
        this.incidentRepository = incidentRepository;
        this.geminiClient = geminiClient;
        this.retrievalService = retrievalService;
    }

    public Incident diagnose(UUID incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new RuntimeException("Incident not found: " + incidentId));

        String searchQuery = incident.getTitle() + " " + incident.getAffectedService();
        List<KbDocument> relevantDocs = retrievalService.findRelevant(searchQuery, 2);

        String prompt = buildPrompt(incident, relevantDocs);
        String diagnosis = geminiClient.generateDiagnosis(prompt);

        incident.setRootCauseGuess(diagnosis);
        incident.setStatus("DIAGNOSING");
        return incidentRepository.save(incident);
    }

    private String buildPrompt(Incident incident, List<KbDocument> relevantDocs) {
        String knowledgeContext = relevantDocs.isEmpty()
                ? "No relevant runbooks found."
                : relevantDocs.stream()
                .map(doc -> "--- " + doc.getTitle() + " ---\n" + doc.getContent())
                .collect(Collectors.joining("\n\n"));

        return """
                You are an AIOps incident diagnosis assistant. Given the following incident
                and relevant runbook excerpts, provide a concise, likely root cause hypothesis
                (2-3 sentences max). Ground your answer in the runbooks where they're relevant,
                and mention which runbook you used if applicable.

                Incident title: %s
                Severity: %s
                Affected service: %s
                Current status: %s

                Relevant runbooks:
                %s
                """.formatted(
                incident.getTitle(),
                incident.getSeverity(),
                incident.getAffectedService(),
                incident.getStatus(),
                knowledgeContext
        );
    }
}
