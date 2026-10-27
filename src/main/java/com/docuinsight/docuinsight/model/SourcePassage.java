package com.docuinsight.docuinsight.model;

// One passage shown to the model and (if cited) returned to the user as a source.
// page is null for TXT/CSV and for PDFs extracted before . score is null for small docs (no search ran).
public record SourcePassage(int number, Integer page, int chunkIndex, String text, Double score) {
}