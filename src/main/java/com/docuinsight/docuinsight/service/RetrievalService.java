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
import java.util.List;

@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    static final int SMALL_DOC_CHARS = 12_000;   // at or below this, send every chunk (whole document)
    static final int TOP_K = 5;                  // chunks sent for large docs
    static final double MIN_SIMILARITY = 0.3;    // drop weak matches

    // What the caller gets: numbered passages (for sources) + the same passages as prompt text
    public record RetrievalResult(List<SourcePassage> passages, String context) {
    }

    private final VectorStore vectorStore;
    private final EmbeddingIngestionService ingestionService;
    private final ChunkingService chunkingService;

    public RetrievalService(VectorStore vectorStore,
                            EmbeddingIngestionService ingestionService,
                            ChunkingService chunkingService) {
        this.vectorStore = vectorStore;
        this.ingestionService = ingestionService;
        this.chunkingService = chunkingService;
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

    // Small document: every chunk, in order.
    private List<SourcePassage> allChunks(UploadedFile file, String text) {
        List<SourcePassage> passages = new ArrayList<>();
        int number = 1;
        for (TextChunk chunk : chunkingService.chunk(text)) {
            passages.add(new SourcePassage(number++, chunk.page(), chunk.index(), chunk.text(), null));
        }
        log.info("File ID={} is small ({} chars): sending all {} chunks", file.getId(), text.length(), passages.size());
        return passages;
    }

    // Large document: top matching chunks for THIS file AND THIS user
    private List<SourcePassage> searchChunks(UploadedFile file, Long userId, String question) {
        if (file.getIndexedAt() == null) {
            ingestionService.ingest(file); // indexing failed earlier: heal it now
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

        List<SourcePassage> passages = new ArrayList<>();
        int number = 1;
        for (Document hit : hits) {
            Integer page = toInteger(hit.getMetadata().get("page"));
            Integer chunkIndex = toInteger(hit.getMetadata().get("chunkIndex"));
            log.info("  [{}] chunk {} page {} score {}", number, chunkIndex, page, hit.getScore());
            passages.add(new SourcePassage(number++, page,
                    chunkIndex == null ? -1 : chunkIndex, hit.getText(), hit.getScore()));
        }
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

    // Metadata comes back from JSON: numbers may be Integer, Long or Double
    private static Integer toInteger(Object value) {
        return (value instanceof Number n) ? n.intValue() : null;
    }
}