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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import org.springframework.http.HttpStatusCode;

import java.time.Duration;

@Service
public class LLMService {

    private static final Logger log = LoggerFactory.getLogger(LLMService.class);
    private static final int MAX_TEXT_CHARS = 30_000;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    @Value("${gemini.api.timeout:30}")
    private int timeoutSeconds;

    @Value("${gemini.api.max-tokens:8192}")
    private int maxTokens;

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

        // DEBUG: Log the API key
        log.info("===== LLM SERVICE DEBUG =====");
        log.info("API Key: {}", (apiKey != null ? apiKey.substring(0, Math.min(10, apiKey.length())) + "..." : "NULL"));
        log.info("API Key Length: {}", (apiKey != null ? apiKey.length() : 0));
        log.info("API URL: {}", apiUrl);
        log.info("=============================");

        String safeText  = truncateText(extractedText);
        String fullPrompt = prompt + "\n\n--- DOCUMENT CONTENT ---\n\n" + safeText;
        String requestBody = buildRequestBody(fullPrompt);

        log.info("Calling Gemini API. Text length: {} chars", safeText.length());

        try {
            String response = webClient.post()
                    .uri(apiUrl + "?key=" + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status == HttpStatus.TOO_MANY_REQUESTS, clientResponse ->
                            Mono.error(new RuntimeException(
                                    "Gemini API rate limit reached. Please wait and try again.")))
                    .onStatus(status -> status == HttpStatus.UNAUTHORIZED, clientResponse ->
                            Mono.error(new RuntimeException(
                                    "Invalid Gemini API key. Check your configuration.")))
                    .onStatus(status -> status == HttpStatus.BAD_REQUEST, clientResponse ->
                            clientResponse.bodyToMono(String.class).flatMap(body ->
                                    Mono.error(new RuntimeException(
                                            "Gemini rejected the request: " + extractGeminiError(body)))))
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            Mono.error(new RuntimeException(
                                    "Gemini service is temporarily unavailable. Try again later.")))
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            return parseGeminiResponse(response);

        } catch (WebClientResponseException e) {
            log.error("WebClient error: status={}", e.getStatusCode());
            throw new RuntimeException("Gemini API error: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error calling Gemini", e);
            throw new RuntimeException("Unexpected error contacting Gemini AI: " + e.getMessage(), e);
        }
    }

    private String parseGeminiResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new RuntimeException("Gemini returned an empty response.");
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            if (root.has("error")) {
                throw new RuntimeException("Gemini API error: " +
                                           root.path("error").path("message").asText("Unknown error"));
            }

            JsonNode candidates = root.path("candidates");
            if (candidates.isMissingNode() || candidates.isEmpty()) {
                JsonNode feedback = root.path("promptFeedback");
                if (!feedback.isMissingNode()) {
                    throw new RuntimeException("Gemini blocked the request: " +
                                               feedback.path("blockReason").asText("UNKNOWN"));
                }
                throw new RuntimeException("Gemini returned no candidates in response.");
            }

            JsonNode firstCandidate = candidates.get(0);
            String finishReason = firstCandidate.path("finishReason").asText("STOP");

            if ("SAFETY".equals(finishReason)) {
                throw new RuntimeException("Gemini blocked the response due to safety filters.");
            }
            if ("RECITATION".equals(finishReason)) {
                throw new RuntimeException("Gemini stopped due to recitation policy.");
            }
            if ("MAX_TOKENS".equals(finishReason)) {
                log.warn("Gemini hit max tokens — response may be truncated.");
            }

            JsonNode parts = firstCandidate.path("content").path("parts");
            if (parts.isMissingNode() || parts.isEmpty()) {
                throw new RuntimeException("Gemini response has no content parts.");
            }

            StringBuilder result = new StringBuilder();
            for (JsonNode part : parts) {
                result.append(part.path("text").asText(""));
            }

            String finalText = result.toString().trim();
            if (finalText.isBlank()) {
                throw new RuntimeException("Gemini generated an empty report. Try again.");
            }

            log.info("Gemini response received. Output length: {} chars", finalText.length());
            return finalText;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Gemini response", e);
            throw new RuntimeException("Failed to parse Gemini response: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(String prompt) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode contents = root.putArray("contents");
            ObjectNode contentNode = contents.addObject();
            contentNode.putArray("parts").addObject().put("text", prompt);

            ObjectNode generationConfig = root.putObject("generationConfig");
            generationConfig.put("maxOutputTokens", maxTokens);
            generationConfig.put("temperature", 0.3);
            generationConfig.put("topP", 0.8);
            generationConfig.put("topK", 40);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Gemini request body", e);
        }
    }

    private String truncateText(String text) {
        if (text.length() <= MAX_TEXT_CHARS) return text;
        log.warn("Text truncated from {} to {} chars", text.length(), MAX_TEXT_CHARS);
        return text.substring(0, MAX_TEXT_CHARS) +
               "\n\n[Document truncated. Analysis based on first portion.]";
    }

    private String extractGeminiError(String body) {
        try {
            return objectMapper.readTree(body).path("error").path("message").asText(body);
        } catch (Exception e) {
            return body;
        }
    }
}