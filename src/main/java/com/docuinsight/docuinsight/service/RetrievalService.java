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
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    static final int SMALL_DOC_CHARS = 12_000;   // at or below this, send every chunk (whole document)
    static final int TOP_K = 20;                  // chunks per search query
    static final int MAX_PER_PAGE = 3;           // one dense page can't take every slot
    static final int MAX_PASSAGES = 8;           // chunks sent to the model after merging
    static final double MIN_SIMILARITY = 0.25;
    static final int OVERVIEW_PASSAGES = 8;      // overview questions: spread of chunks across the whole document
    private static final Pattern OVERVIEW = Pattern.compile(
            "\\b(what\\s+(is|'s)\\s+(this|the)\\s+(document|file|pdf|paper|report|book)\\s+about"
                    + "|summar(y|ise|ize)|overview|gist|tl;?dr|main\\s+(idea|topic|points?)|key\\s+(points|takeaways))\\b",
            Pattern.CASE_INSENSITIVE);

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

        List<SourcePassage> passages;
        if (text.length() <= SMALL_DOC_CHARS) {
            passages = allChunks(file, text);
        } else if (OVERVIEW.matcher(question).find()) {
            passages = overviewChunks(file, text);
        } else {
            passages = searchChunks(file, userId, question);
        }

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

        // 3. Merge: no duplicate ids or identical text, at most MAX_PER_PAGE chunks per page (Day 20)
        Map<String, Document> picked = new LinkedHashMap<>();
        Set<String> seenText = new HashSet<>();
        Map<Integer, Integer> perPage = new HashMap<>();

        // Pass 1: the best chunk of EACH query, so every sub-question is represented
        for (List<Document> hits : perQuery) {
            if (!hits.isEmpty()) {
                tryPick(hits.get(0), picked, seenText, perPage, false);
            }
        }

        List<Document> ranked = perQuery.stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparingDouble(RetrievalService::scoreOf).reversed())
                .toList();

        // Pass 2: highest scores first, but max MAX_PER_PAGE per page
        for (Document hit : ranked) {
            if (picked.size() >= MAX_PASSAGES) break;
            tryPick(hit, picked, seenText, perPage, true);
        }

        // Pass 3: slots still free (answer really is on one page) -> fill without the page cap
        for (Document hit : ranked) {
            if (picked.size() >= MAX_PASSAGES) break;
            tryPick(hit, picked, seenText, perPage, false);
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

    // Adds a hit unless it is already picked, has identical text, or (capPages) its page is full
    private static void tryPick(Document hit, Map<String, Document> picked, Set<String> seenText,
                                Map<Integer, Integer> perPage, boolean capPages) {
        if (picked.containsKey(hit.getId())) {
            return;
        }
        String text = hit.getText() == null ? "" : hit.getText().strip();
        if (seenText.contains(text)) {
            return;                                   // repeated headers / duplicate pages waste slots
        }
        Integer page = toInteger(hit.getMetadata().get("page"));
        int onPage = perPage.getOrDefault(page, 0);
        if (capPages && page != null && onPage >= MAX_PER_PAGE) {
            return;
        }
        picked.put(hit.getId(), hit);
        seenText.add(text);
        perPage.put(page, onPage + 1);
    }

    private static double scoreOf(Document d) {

        return d.getScore() == null ? 0.0 : d.getScore();
    }

    // Metadata comes back from JSON: numbers may be Integer, Long or Double
    private static Integer toInteger(Object value) {
        return (value instanceof Number n) ? n.intValue() : null;
    }

    // Overview question on a large document: no single chunk matches "what is this about",
    // so similarity search finds nothing. Send the opening (title/abstract) + an even spread to the end.
    private List<SourcePassage> overviewChunks(UploadedFile file, String text) {
        List<TextChunk> chunks = chunkingService.chunk(text);
        int n = chunks.size();

        TreeSet<Integer> picks = new TreeSet<>();               // sorted, no duplicates
        for (int i = 0; i < Math.min(2, n); i++) {
            picks.add(i);                                        // title + abstract / intro
        }
        int spread = OVERVIEW_PASSAGES - picks.size();
        for (int s = 1; s <= spread && n > 2; s++) {
            picks.add(2 + (int) Math.round((double) s * (n - 3) / spread)); // ... evenly to the last chunk
        }

        List<SourcePassage> passages = new ArrayList<>();
        int number = 1;
        for (int i : picks) {
            TextChunk c = chunks.get(i);
            passages.add(new SourcePassage(number++, c.page(), c.index(), c.text(), null));
        }
        log.info("File ID={} overview question: sending {} of {} chunks {}", file.getId(), passages.size(), n, picks);
        return passages;
    }

}

