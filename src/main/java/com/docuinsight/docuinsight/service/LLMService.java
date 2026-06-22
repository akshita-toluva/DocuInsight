package com.docuinsight.docuinsight.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class LLMService {

    private static final Logger log = LoggerFactory.getLogger(LLMService.class);
    private static final int MAX_TEXT_CHARS = 30_000;

    // Groq config
    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.api.url}")
    private String groqApiUrl;

    @Value("${groq.api.model:llama-3.3-70b-versatile}")
    private String groqModel;

    @Value("${groq.api.timeout:90}")
    private int timeoutSeconds;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public LLMService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
    }

    public String generateReport(String extractedText, String prompt) {
        if (extractedText == null || extractedText.isBlank()) {
            throw new IllegalArgumentException("Extracted text is empty — cannot generate report.");
        }

        String safeText = truncateText(extractedText);
        String fullPrompt = prompt + "\n\n--- DOCUMENT CONTENT ---\n\n" + safeText;

        log.info("Generating report via Groq. Text length: {} chars", safeText.length());

        return callGroq(fullPrompt);
    }

    public String answerQuestion(String extractedText,String question)
    {
        if(extractedText==null || extractedText.isBlank())
        {
            throw new IllegalArgumentException(
                    "No document text available to answer from."
            );
        }
        if(question==null || question.isBlank())
        {
            throw new IllegalArgumentException(
                    "Question cannot be empty"
            );
        }

        //Handle the 30000 char limit
        String safeText=truncateText(extractedText);

        //Build the Q&A prompt
        //Stay inside the document itself
        //The last sentence prevents hallucination when answer is absent
        String prompt="You are a document expert. Answer the following question " +
                "based ONLY on the document content provided below. " +
                "If the answer is not in the document, state that clearly.\n\n" +
                "Question: " + question + "\n\n" +
                "--- DOCUMENT CONTENT ---\n\n" + safeText;

        log.info("Answering Q&A via Groq. Question: {}", question);

        return callGroq(prompt);
    }

    private String callGroq(String prompt) {
        try {
            String requestBody = buildGroqRequestBody(prompt);

            String response = webClient.post()
                    .uri(groqApiUrl)
                    .header("Authorization", "Bearer " + groqApiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status == HttpStatus.TOO_MANY_REQUESTS, clientResponse ->
                            clientResponse.bodyToMono(String.class).flatMap(body -> {
                                log.warn("Groq rate limit hit: {}", body);
                                return Mono.error(new RuntimeException(
                                        "Groq rate limit hit. Please wait a moment and try again."));
                            }))
                    .onStatus(status -> status == HttpStatus.UNAUTHORIZED, clientResponse ->
                            Mono.error(new RuntimeException(
                                    "Invalid Groq API key. Check your groq.api.key in application.properties.")))
                    .onStatus(status -> status == HttpStatus.BAD_REQUEST, clientResponse ->
                            clientResponse.bodyToMono(String.class).flatMap(body ->
                                    Mono.error(new RuntimeException(
                                            "Groq rejected the request: " + body))))
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            Mono.error(new RuntimeException(
                                    "Groq service temporarily unavailable. Try again later.")))
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            return parseGroqResponse(response);

        } catch (WebClientResponseException e) {
            log.error("Groq WebClient error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Groq API error: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error calling Groq", e);
            throw new RuntimeException("Unexpected error contacting Groq AI: " + e.getMessage(), e);
        }
    }

    private String parseGroqResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new RuntimeException("Groq returned an empty response.");
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Check for error field
            if (root.has("error")) {
                throw new RuntimeException("Groq API error: " +
                        root.path("error").path("message").asText("Unknown error"));
            }

            // Parse OpenAI-compatible response format
            JsonNode choices = root.path("choices");
            if (choices.isMissingNode() || choices.isEmpty()) {
                throw new RuntimeException("Groq returned no choices in response.");
            }

            String content = choices.get(0)
                    .path("message")
                    .path("content")
                    .asText("");

            if (content.isBlank()) {
                throw new RuntimeException("Groq generated an empty report. Try again.");
            }

            log.info("Groq response received successfully. Output length: {} chars", content.length());
            return content;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Groq response", e);
            throw new RuntimeException("Failed to parse Groq response: " + e.getMessage(), e);
        }
    }

    private String buildGroqRequestBody(String prompt) {
        try {
            ObjectNode root = objectMapper.createObjectNode();

            root.put("model", groqModel);
            root.put("temperature", 0.3);
            root.put("max_tokens", 4096);

            ArrayNode messages = root.putArray("messages");

            // System message
            ObjectNode systemMsg = messages.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content",
                    "You are a professional document analyst. " +
                            "Provide clear, structured, and insightful analysis of documents.");

            // User message with the actual prompt
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Groq request body", e);
        }
    }

    private String truncateText(String text) {
        if (text.length() <= MAX_TEXT_CHARS) return text;
        log.warn("Text truncated from {} to {} chars", text.length(), MAX_TEXT_CHARS);
        return text.substring(0, MAX_TEXT_CHARS) +
                "\n\n[Document truncated. Analysis based on first portion.]";
    }
}
