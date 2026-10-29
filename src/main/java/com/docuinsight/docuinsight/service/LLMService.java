package com.docuinsight.docuinsight.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;


@Service
public class LLMService {

    private static final Logger log = LoggerFactory.getLogger(LLMService.class);
    private static final int MAX_TEXT_CHARS = 30_000;
    public static final String NOT_IN_DOCUMENT = "NOT_IN_DOCUMENT";
    private static final int MAX_QUERIES = 3;

    private static final String SYSTEM_PROMPT =
            "You are a professional document analyst. " +
                    "Provide clear, structured, and insightful analysis of documents.";

    private final ChatClient chatClient;

    // Spring AI auto configure ChatClient.Builder from the spring.ai.openai.* properties
    public LLMService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    public String generateReport(String extractedText, String prompt) {
        if (extractedText == null || extractedText.isBlank()) {
            throw new IllegalArgumentException("Extracted text is empty — cannot generate report.");
        }

        String safeText = truncateText(extractedText);
        String fullPrompt = prompt + "\n\n--- DOCUMENT CONTENT ---\n\n" + safeText;

        log.info("Generating report via Groq. Text length: {} chars", safeText.length());
        return callModel(fullPrompt);
    }

    public String answerQuestion(String extractedText, String question) {
        if (extractedText == null || extractedText.isBlank()) {
            throw new IllegalArgumentException("No document text available to answer from.");
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question cannot be empty");
        }

        String safeText = truncateText(extractedText);

        // Stay inside the document; the last sentence prevents made-up answers
        // Facts must come from the passages; analysis (strengths, weaknesses, gaps) may be reasoned FROM them (Day 17)
        String prompt = "You are a document analyst. Answer the question using ONLY the numbered passages below.\n" +
                "Rules:\n" +
                "1. Facts: state only facts found in the passages and cite the passage number(s) after each, e.g. [2].\n" +
                "2. Assessment questions (strengths, weaknesses, disadvantages, gaps, risks, suitability, recommendations) " +
                "are answerable whenever the passages describe the person, product or topic being assessed: list points " +
                "supported by the passages (cite them), note what the document does not show (missing skills, evidence or details), " +
                "and say it is an assessment based on the document. Do NOT reply " + NOT_IN_DOCUMENT + " for these.\n" +
                "3. Factual questions only: if the passages contain nothing relevant, reply with exactly " + NOT_IN_DOCUMENT +
                " and nothing else.\n" +
                "4. If they contain only part of the answer, answer that part and say which part is not in the document.\n" +
                "5. Never invent facts, numbers, names or dates that are not in the passages.\n\n" +
                "Question: " + question + "\n\n" +
                "--- PASSAGES ---\n\n" + safeText;

        log.info("Answering Q&A via Groq. Question: {}", question);
        return callModel(prompt);
    }

    // Split one user question into 1-3 standalone search queries.
    public List<String> toSearchQueries(String question) {
        String prompt = "Rewrite the user's question into standalone search queries for a document search engine.\n" +
                "Rules:\n" +
                "1. If the question asks about several different things, write one query per thing (maximum " + MAX_QUERIES + ").\n" +
                "2. If it asks about one thing, write exactly one query.\n" +
                "3. Keep names, numbers and key terms exactly as written. Do not answer the question.\n" +
                "4. Output only the queries, one per line, with no numbering or extra text.\n\n" +
                "Question: " + question;
        try {
            List<String> queries = callModel(prompt, "low").lines()
                    .map(line -> line.replaceFirst("^\\s*(?:[-*•]|\\d+[.)])\\s*", "").trim()) // strip "1." / "-" if the model adds them
                    .filter(line -> !line.isBlank())
                    .distinct()
                    .limit(MAX_QUERIES)
                    .toList();
            log.info("Search queries for '{}': {}", question, queries);
            return queries.isEmpty() ? List.of(question) : queries;
        } catch (RuntimeException e) {
            log.warn("Query rewrite failed, searching with the original question: {}", e.getMessage());
            return List.of(question);
        }
    }

    // One place that calls the model: build the prompt, send it, return the text
    private String callModel(String prompt) {
        return callModel(prompt, null);
    }

    // reasoningEffort: null = model default; "low" = fast, for small helper tasks (Day 15)
    private String callModel(String prompt, String reasoningEffort) {
        String content;
        try {
            var request = chatClient.prompt().user(prompt);
            if (reasoningEffort != null) {
                request = request.options(OpenAiChatOptions.builder().reasoningEffort(reasoningEffort));
            }
            content = request.call().content();
        } catch (RuntimeException e) {
            throw translateError(e);
        }

        if (content == null || content.isBlank()) {
            throw new RuntimeException("The AI returned an empty response. Try again.");
        }
        log.info("Groq response received successfully. Output length: {} chars", content.length());
        return content;
    }

    // Turn low-level client errors into the messages the controllers already understand
    // (ReportController maps a message containing "rate limit" to HTTP 429)
    private RuntimeException translateError(RuntimeException e) {
        String details = describe(e);

        if (details.contains("ratelimit") || details.contains("429")) {
            log.warn("Groq rate limit hit: {}", e.getMessage());
            return new RuntimeException("Groq rate limit hit. Please wait a moment and try again.", e);
        }
        if (details.contains("unauthorized") || details.contains("401")) {
            log.error("Groq rejected the API key");
            return new RuntimeException("Invalid Groq API key. Check groq.api.key in application-local.properties.", e);
        }
        log.error("AI call failed", e);
        return new RuntimeException("AI service error: " + e.getMessage(), e);
    }

    // Class names + messages of the whole cause chain, lower-case, for simple matching
    private String describe(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            sb.append(t.getClass().getSimpleName()).append(' ')
                    .append(t.getMessage()).append(' ');
        }
        return sb.toString().toLowerCase();
    }

    private String truncateText(String text) {
        if (text.length() <= MAX_TEXT_CHARS) return text;
        log.warn("Text truncated from {} to {} chars", text.length(), MAX_TEXT_CHARS);
        return text.substring(0, MAX_TEXT_CHARS) +
                "\n\n[Document truncated. Analysis based on first portion.]";
    }
}