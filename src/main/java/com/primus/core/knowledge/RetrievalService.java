package com.primus.core.knowledge;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class RetrievalService {

    private final KbDocumentRepository kbDocumentRepository;
    private final EmbeddingClient embeddingClient;

    public RetrievalService(KbDocumentRepository kbDocumentRepository, EmbeddingClient embeddingClient) {
        this.kbDocumentRepository = kbDocumentRepository;
        this.embeddingClient = embeddingClient;
    }

    public List<KbDocument> findRelevant(String queryText, int topK) {
        var queryEmbedding = embeddingClient.embed(queryText);
        var allDocs = kbDocumentRepository.findAll();

        return allDocs.stream()
                .filter(doc -> doc.getEmbedding() != null)
                .sorted(Comparator.comparingDouble(
                        (KbDocument doc) -> VectorMath.cosineSimilarity(
                                queryEmbedding,
                                EmbeddingSerializer.fromJson(doc.getEmbedding())
                        )
                ).reversed())
                .limit(topK)
                .toList();
    }

    /** All KB documents, for plain browsing -- no Gemini call, just a DB read. */
    public List<KbDocument> getAllDocuments() {
        return kbDocumentRepository.findAll();
    }

    /**
     * Same ranking as findRelevant(), but also returns each document's real
     * cosine similarity score (previously computed only for sorting, then
     * discarded) so the UI can show an actual relevance percentage instead
     * of inventing one.
     */
    public List<ScoredDocument> searchWithScores(String queryText, int topK) {
        var queryEmbedding = embeddingClient.embed(queryText);
        var allDocs = kbDocumentRepository.findAll();

        return allDocs.stream()
                .filter(doc -> doc.getEmbedding() != null)
                .map(doc -> new ScoredDocument(doc, VectorMath.cosineSimilarity(
                        queryEmbedding, EmbeddingSerializer.fromJson(doc.getEmbedding()))))
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                .limit(topK)
                .toList();
    }
}