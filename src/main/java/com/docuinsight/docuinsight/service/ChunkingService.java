package com.docuinsight.docuinsight.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits extracted document text into small, overlapping chunks for RAG.
 * Each chunk keeps the page it came from (from the "--- Page N ---" markers written by TextExtractionService).
 * Chunks never cross a page boundary, so every chunk has exactly one page number.
 */
@Service
public class ChunkingService {

    /** One piece of a document. page is null when the source has no pages (CSV, TXT, DOCX, image). */
    public record TextChunk(int index, Integer page, String text) {
    }

    // all-MiniLM-L6-v2 reads up to ~256 word-pieces; ~800 characters (~200 tokens) stays safely inside that
    public static final int DEFAULT_CHUNK_SIZE = 800;
    // Repeat the last ~150 characters at the start of the next chunk so a sentence cut at the edge is not lost
    public static final int DEFAULT_OVERLAP = 150;

    // A whole line like "--- Page 12 ---"
    private static final Pattern PAGE_MARKER = Pattern.compile(
            "^" + Pattern.quote(TextExtractionService.PAGE_MARKER_PREFIX) + "(\\d+)"
                    + Pattern.quote(TextExtractionService.PAGE_MARKER_SUFFIX) + "\\s*$",
            Pattern.MULTILINE);

    public List<TextChunk> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public List<TextChunk> chunk(String text, int chunkSize, int overlap) {
        if (chunkSize <= 0 || overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("Need chunkSize > 0 and 0 <= overlap < chunkSize");
        }
        List<TextChunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        // Windows line endings (\r\n) -> \n so splitting and markers behave the same everywhere
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');

        for (PageText page : splitIntoPages(normalized)) {
            for (String piece : slidingWindow(page.text(), chunkSize, overlap)) {
                chunks.add(new TextChunk(chunks.size(), page.number(), piece));
            }
        }
        return chunks;
    }

    // ---------------- pages ----------------

    private record PageText(Integer number, String text) {
    }

    private List<PageText> splitIntoPages(String text) {
        List<PageText> pages = new ArrayList<>();
        Matcher m = PAGE_MARKER.matcher(text);

        Integer currentPage = null; // text before the first marker (or no markers at all) has no page
        int contentStart = 0;
        while (m.find()) {
            addIfNotBlank(pages, currentPage, text.substring(contentStart, m.start()));
            currentPage = Integer.parseInt(m.group(1));
            contentStart = m.end();
        }
        addIfNotBlank(pages, currentPage, text.substring(contentStart));
        return pages;
    }

    private void addIfNotBlank(List<PageText> pages, Integer page, String text) {
        String trimmed = text.trim();
        if (!trimmed.isEmpty()) {
            pages.add(new PageText(page, trimmed));
        }
    }

    // ---------------- sliding window ----------------

    private List<String> slidingWindow(String text, int size, int overlap) {
        List<String> pieces = new ArrayList<>();
        int length = text.length();
        int start = 0;

        while (start < length) {
            int end = Math.min(start + size, length);
            if (end < length) {
                end = niceCut(text, start, end, size);
            }

            String piece = text.substring(start, end).trim();
            if (!piece.isEmpty()) {
                pieces.add(piece);
            }
            if (end >= length) {
                break;
            }

            // Step back by the overlap, then move forward to the start of a word
            int next = end - overlap;
            while (next < end && next > 0 && !Character.isWhitespace(text.charAt(next - 1))) {
                next++; // do not start the next chunk in the middle of a word
            }
            start = Math.max(next, start + 1); // always move forward: no endless loop
        }
        return pieces;
    }

    // Prefer to end a chunk at a paragraph, then a sentence, then a line, then a space —
    // but only in the last 30% of the window, so chunks do not become tiny
    private int niceCut(String text, int start, int end, int size) {
        int earliest = start + (int) (size * 0.7);
        int cut = lastBreak(text, "\n\n", earliest, end);
        if (cut < 0) cut = lastSentenceEnd(text, earliest, end);
        if (cut < 0) cut = lastBreak(text, "\n", earliest, end);
        if (cut < 0) cut = lastBreak(text, " ", earliest, end);
        return cut > start ? cut : end; // no break found: hard cut
    }

    private int lastBreak(String text, String sep, int from, int to) {
        int i = text.lastIndexOf(sep, to - sep.length());
        return i >= from ? i + sep.length() : -1;
    }

    private int lastSentenceEnd(String text, int from, int to) {
        for (int i = to - 1; i >= from; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && i + 1 < text.length() && Character.isWhitespace(text.charAt(i + 1))) {
                return i + 1;
            }
        }
        return -1;
    }
}