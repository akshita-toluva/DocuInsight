package com.docuinsight.docuinsight.service;

import com.docuinsight.docuinsight.model.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    static final int SMALL_DOC_CHARS = 12_000;   // at or below this, send the whole text
    static final int TOP_K = 5;                  // chunks sent for large docs
    static final double MIN_SIMILARITY = 0.3;    // drop weak matches (tuned on Day 16)

    private final VectorStore vectorStore;
    private final EmbeddingIngestionService ingestionService;

    public RetrievalService(VectorStore vectorStore, EmbeddingIngestionService ingestionService) {
        this.vectorStore = vectorStore;
        this.ingestionService = ingestionService;
    }

    // Returns the text the LLM should answer from. Empty string = nothing relevant found.
    public String buildContext(UploadedFile file, Long userId, String question) {
        String text = file.getExtractedText();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("No extracted text found. Extract from the file first.");
        }

        // Small document: whole text is cheaper and more complete than 5 chunks
        if (text.length() <= SMALL_DOC_CHARS) {
            log.info("File ID={} is small ({} chars): using whole text", file.getId(), text.length());
            return text;
        }

        // Large document never indexed (indexing failed earlier): index now
        if (file.getIndexedAt() == null) {
            ingestionService.ingest(file);
        }

        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(TOP_K)
                .similarityThreshold(MIN_SIMILARITY)
                // SECURITY: always both. Without userId, one user could read another's chunks.
                .filterExpression("fileId == " + file.getId() + " && userId == " + userId)
                .build();

        List<Document> hits = vectorStore.similaritySearch(request);
        log.info("File ID={} retrieval: {} chunks for question '{}'", file.getId(), hits.size(), question);

        StringBuilder context = new StringBuilder();
        for (Document hit : hits) {
            Object page = hit.getMetadata().get("page");
            log.info("  chunk {} page {} score {}", hit.getMetadata().get("chunkIndex"), page, hit.getScore());
            context.append("[Passage")
                    .append(page != null ? " | page " + page : "")
                    .append("]\n")
                    .append(hit.getText())
                    .append("\n\n");
        }
        return context.toString().trim();
    }
}