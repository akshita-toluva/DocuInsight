package com.docuinsight.docuinsight.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class LLMService {

    private static final Logger log = LoggerFactory.getLogger(LLMService.class);
    private static final int MAX_TEXT_CHARS = 30_000;

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
        String prompt = "You are a document expert. Answer the following question " +
                "based ONLY on the document content provided below. " +
                "If the answer is not in the document, state that clearly.\n\n" +
                "Question: " + question + "\n\n" +
                "--- DOCUMENT CONTENT ---\n\n" + safeText;

        log.info("Answering Q&A via Groq. Question: {}", question);
        return callModel(prompt);
    }

    // One place that calls the model: build the prompt, send it, return the text
    private String callModel(String prompt) {
        String content;
        try {
            content = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
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