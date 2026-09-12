package com.primus.core.knowledge;

import org.springframework.stereotype.Service;

@Service
public class KnowledgeBaseService {

    private final KbDocumentRepository kbDocumentRepository;
    private final EmbeddingClient embeddingClient;

    public KnowledgeBaseService(KbDocumentRepository kbDocumentRepository, EmbeddingClient embeddingClient) {
        this.kbDocumentRepository = kbDocumentRepository;
        this.embeddingClient = embeddingClient;
    }

    public KbDocument addDocument(String docType, String title, String content) {
        KbDocument doc = new KbDocument(docType, title, content);

        // Embed title + content together so search matches on both
        var embedding = embeddingClient.embed(title + "\n\n" + content);
        doc.setEmbedding(EmbeddingSerializer.toJson(embedding));

        return kbDocumentRepository.save(doc);
    }
}
