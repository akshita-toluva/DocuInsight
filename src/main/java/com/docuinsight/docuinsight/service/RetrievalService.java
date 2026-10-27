package com.docuinsight.docuinsight.service;

import com.docuinsight.docuinsight.model.SourcePassage;
import com.docuinsight.docuinsight.model.UploadedFile;
import com.docuinsight.docuinsight.service.ChunkingService.TextChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    static final int SMALL_DOC_CHARS = 12_000;   // at or below this, send every chunk (whole document)
    static final int TOP_K = 5;                  // chunks per search query
    static final int MAX_PASSAGES = 6;           // chunks sent to the model after merging (Day 15)
    static final double MIN_SIMILARITY = 0.3;    // drop weak matches (tuned on Day 16)

    public record RetrievalResult(List<SourcePassage> passages, String context) {
    }

    private final VectorStore vectorStore;
    private final EmbeddingIngestionService ingestionService;
    private final ChunkingService chunkingService;
    private final LLMService llmService;

    public RetrievalService(VectorStore vectorStore,
                            EmbeddingIngestionService ingestionService,
                            ChunkingService chunkingService,
                            LLMService llmService) {
        this.vectorStore = vectorStore;
        this.ingestionService = ingestionService;
        this.chunkingService = chunkingService;
        this.llmService = llmService;
    }

    public RetrievalResult retrieve(UploadedFile file, Long userId, String question) {
        String text = file.getExtractedText();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("No extracted text found. Extract from the file first.");
        }

        List<SourcePassage> passages = (text.length() <= SMALL_DOC_CHARS)
                ? allChunks(file, text)
                : searchChunks(file, userId, question);

        return new RetrievalResult(passages, toContext(passages));
    }

    private List<SourcePassage> allChunks(UploadedFile file, String text) {
        List<SourcePassage> passages = new ArrayList<>();
        int number = 1;
        for (TextChunk chunk : chunkingService.chunk(text)) {
            passages.add(new SourcePassage(number++, chunk.page(), chunk.index(), chunk.text(), null));
        }
        log.info("File ID={} is small ({} chars): sending all {} chunks", file.getId(), text.length(), passages.size());
        return passages;
    }

    private List<SourcePassage> searchChunks(UploadedFile file, Long userId, String question) {
        if (file.getIndexedAt() == null) {
            ingestionService.ingest(file); // indexing failed earlier: heal it now
        }

        // 1. Queries: original first (safe baseline), then the rewritten sub-questions
        List<String> queries = new ArrayList<>();
        queries.add(question);
        if (looksMultiPart(question)) {
            for (String q : llmService.toSearchQueries(question)) {
                if (!q.equalsIgnoreCase(question.trim())) {
                    queries.add(q);
                }
            }
        }

        // SECURITY: always both. Without userId, one user could read another's chunks.
        String filter = "fileId == " + file.getId() + " && userId == " + userId;

        // 2. One vector search per query
        List<List<Document>> perQuery = new ArrayList<>();
        for (String q : queries) {
            List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(q)
                    .topK(TOP_K)
                    .similarityThreshold(MIN_SIMILARITY)
                    .filterExpression(filter)
                    .build());
            log.info("File ID={} query '{}': {} chunks", file.getId(), q, hits.size());
            perQuery.add(hits);
        }

        // 3. Merge without duplicates (same chunk found by two queries = same document id)
        Map<String, Document> picked = new LinkedHashMap<>();

        // Pass 1: the best chunk of EACH query, so every sub-question is represented
        for (List<Document> hits : perQuery) {
            if (!hits.isEmpty()) {
                picked.putIfAbsent(hits.get(0).getId(), hits.get(0));
            }
        }

        // Pass 2: fill the remaining slots with the highest scores overall
        perQuery.stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparingDouble(RetrievalService::scoreOf).reversed())
                .forEach(hit -> {
                    if (picked.size() < MAX_PASSAGES) {
                        picked.putIfAbsent(hit.getId(), hit);
                    }
                });

        List<SourcePassage> passages = new ArrayList<>();
        int number = 1;
        for (Document hit : picked.values()) {
            Integer page = toInteger(hit.getMetadata().get("page"));
            Integer chunkIndex = toInteger(hit.getMetadata().get("chunkIndex"));
            log.info("  [{}] chunk {} page {} score {}", number, chunkIndex, page, hit.getScore());
            passages.add(new SourcePassage(number++, page,
                    chunkIndex == null ? -1 : chunkIndex, hit.getText(), hit.getScore()));
        }
        log.info("File ID={} retrieval: {} queries -> {} passages", file.getId(), queries.size(), passages.size());
        return passages;
    }

    // "[1] (page 3)\n<text>" blocks: the numbers the model will cite
    private String toContext(List<SourcePassage> passages) {
        StringBuilder context = new StringBuilder();
        for (SourcePassage p : passages) {
            context.append('[').append(p.number()).append(']');
            if (p.page() != null) {
                context.append(" (page ").append(p.page()).append(')');
            }
            context.append('\n').append(p.text()).append("\n\n");
        }
        return context.toString().trim();
    }

    // Only questions that look multi-part need splitting: saves a Groq call on simple ones
    private static boolean looksMultiPart(String question) {
        String q = question.toLowerCase();
        return q.contains(" and ") || q.contains(",") || q.contains(";") || q.contains(" also ")
                || q.chars().filter(c -> c == '?').count() > 1;
    }

    private static double scoreOf(Document d) {
        return d.getScore() == null ? 0.0 : d.getScore();
    }

    // Metadata comes back from JSON: numbers may be Integer, Long or Double
    private static Integer toInteger(Object value) {
        return (value instanceof Number n) ? n.intValue() : null;
    }
}