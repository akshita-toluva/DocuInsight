package com.docuinsight.docuinsight.service;

import com.docuinsight.docuinsight.model.UploadedFile;
import com.docuinsight.docuinsight.repository.UploadedFileRepository;
import com.docuinsight.docuinsight.service.ChunkingService.TextChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a file's extracted text into vectors:
 * chunk -> embed (local all-MiniLM-L6-v2) -> store in pgvector, with metadata for filtering and citations.
 */
@Service
public class EmbeddingIngestionService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingIngestionService.class);

    private final VectorStore vectorStore;
    private final ChunkingService chunkingService;
    private final UploadedFileRepository fileRepository;

    public EmbeddingIngestionService(VectorStore vectorStore,
                                     ChunkingService chunkingService,
                                     UploadedFileRepository fileRepository) {
        this.vectorStore = vectorStore;
        this.chunkingService = chunkingService;
        this.fileRepository = fileRepository;
    }

    /** Stores all chunks of this file. Safe to call again: old chunks are removed first. Returns the chunk count. */
    public int ingest(UploadedFile file) {
        String text = file.getExtractedText();
        if (text == null || text.isBlank()) {
            return 0;
        }

        // Remove this file's old chunks so re-extraction never leaves duplicates
        vectorStore.delete("fileId == " + file.getId());

        List<Document> documents = new ArrayList<>();
        for (TextChunk chunk : chunkingService.chunk(text)) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("fileId", file.getId());               // which file
            metadata.put("userId", file.getUser().getId());     // whose file: retrieval filters on this (security)
            metadata.put("fileName", file.getFileName());       // for showing sources
            metadata.put("chunkIndex", chunk.index());          // order inside the file
            if (chunk.page() != null) {
                metadata.put("page", chunk.page());             // only PDFs have pages; metadata values must not be null
            }
            documents.add(new Document(chunk.text(), metadata));
        }

        vectorStore.add(documents); // embeds every chunk with the local model, then INSERTs into vector_store

        file.setIndexedAt(LocalDateTime.now());
        fileRepository.save(file);
        log.info("Indexed file ID={} into {} chunks", file.getId(), documents.size());
        return documents.size();
    }
}