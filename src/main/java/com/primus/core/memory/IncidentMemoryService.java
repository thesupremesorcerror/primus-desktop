package com.primus.core.memory;

import com.primus.core.incident.Incident;
import com.primus.core.knowledge.EmbeddingClient;
import com.primus.core.knowledge.EmbeddingSerializer;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class IncidentMemoryService {

    private final IncidentMemoryRepository incidentMemoryRepository;
    private final EmbeddingClient embeddingClient;

    public IncidentMemoryService(IncidentMemoryRepository incidentMemoryRepository, EmbeddingClient embeddingClient) {
        this.incidentMemoryRepository = incidentMemoryRepository;
        this.embeddingClient = embeddingClient;
    }

    public void storeResolvedIncident(Incident incident, String remediationTaken) {
        String summary = incident.getTitle() + " on " + incident.getAffectedService()
                + " -- root cause: " + incident.getRootCauseGuess();

        IncidentMemory memory = new IncidentMemory(
                incident.getId(),
                summary,
                incident.getRootCauseGuess(),
                remediationTaken,
                "SUCCESS"
        );

        var embedding = embeddingClient.embed(summary);
        memory.setEmbedding(EmbeddingSerializer.toJson(embedding));

        incidentMemoryRepository.save(memory);
        System.out.println("[MEMORY] Stored resolved incident: " + incident.getTitle());
    }

    public List<IncidentMemory> findSimilarCases(String queryText, int topK) {
        var queryEmbedding = embeddingClient.embed(queryText);
        var allMemories = incidentMemoryRepository.findAll();

        return allMemories.stream()
                .filter(m -> m.getEmbedding() != null)
                .sorted(Comparator.comparingDouble(
                        (IncidentMemory m) -> com.primus.core.knowledge.VectorMath.cosineSimilarity(
                                queryEmbedding,
                                EmbeddingSerializer.fromJson(m.getEmbedding())
                        )
                ).reversed())
                .limit(topK)
                .toList();
    }

    /** All stored incident memories, for plain browsing -- no Gemini call, just a DB read. */
    public List<IncidentMemory> getAllMemories() {
        return incidentMemoryRepository.findAll();
    }

    /**
     * Same ranking as findSimilarCases(), but also returns each memory's
     * real cosine similarity score instead of discarding it after sorting.
     */
    public List<ScoredMemory> searchWithScores(String queryText, int topK) {
        var queryEmbedding = embeddingClient.embed(queryText);
        var allMemories = incidentMemoryRepository.findAll();

        return allMemories.stream()
                .filter(m -> m.getEmbedding() != null)
                .map(m -> new ScoredMemory(m, com.primus.core.knowledge.VectorMath.cosineSimilarity(
                        queryEmbedding, EmbeddingSerializer.fromJson(m.getEmbedding()))))
                .sorted(Comparator.comparingDouble(ScoredMemory::score).reversed())
                .limit(topK)
                .toList();
    }
}