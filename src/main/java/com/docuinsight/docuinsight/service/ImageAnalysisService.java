package com.docuinsight.docuinsight.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;

@Service
public class ImageAnalysisService {
    @Value("${groq.api.key}")
    private String groqApiKey;
    @Value("${groq.api.url}")
    private String groqApiUrl;
    @Value("${groq.vision.model}")
    private String visionModel;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ImageAnalysisService(WebClient.Builder builder, ObjectMapper objectMapper)
    {
        this.webClient = builder.build();
        this.objectMapper = objectMapper;
    }

    public String analyseImage(String filePath,String mimeType) throws Exception {
        //step 1 : Read Image bytes from disk
        byte[] imageByte= Files.readAllBytes(Paths.get(filePath));

        //step 2 : Encode bytes to base64 text string
        String base64Image= Base64.getEncoder().encodeToString(imageByte);

        //step 3 : Build json request body for Groq Vision API
        String requestBody=buildVisionRequestBody(base64Image,mimeType);

        // Step 4: Send to Groq and return AI description
        return callGroqVision(requestBody);
    }

    // METHOD — called by TextExtractionService for in-memory PDF page images
    // Accepts Base64 string directly, skipping the disk read step
    public String analyseImageFromBase64(String base64Image, String mimeType) throws Exception{
        String requestBody=buildVisionRequestBody(base64Image,mimeType);
        return callGroqVision(requestBody);
    }

    private String buildVisionRequestBody(String base64Image, String mimeType) throws Exception {
        ObjectNode root=objectMapper.createObjectNode();
        root.put("model",visionModel);
        root.put("temperature",0.2);
        root.put("max_tokens",2048);

        ArrayNode messages=root.putArray("messages");

        /*
        //System Messages
        ObjectNode sysMsg=messages.addObject();
        sysMsg.put("role","system");
        sysMsg.put("content","You are an expert image analyst. Describe images clearly and in detail."); */


        //User message -- content in array not a string
        ObjectNode userMsg=messages.addObject();
        userMsg.put("role","user");
        ArrayNode contentArray=userMsg.putArray("content");

        //Element 1 : the image
        ObjectNode imageEl=contentArray.addObject();
        imageEl.put("type","image_url");
        imageEl.putObject("image_url")
                .put("url","data:" + mimeType + ";base64," + base64Image);

        //Element 2 : the text question
        ObjectNode textEl=contentArray.addObject();
        textEl.put("type","text");
        textEl.put("text","You are an expert image analyst. Analyse this image in detail. Describe all text, charts, diagrams, people, objects, or data visible.");

        return objectMapper.writeValueAsString(root);
    }

    private String callGroqVision(String requestBody) throws Exception {
        String response=webClient.post()
                .uri(groqApiUrl)
                .header("Authorization", "Bearer " + groqApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), clientResponse ->
                clientResponse.bodyToMono(String.class).flatMap(body -> {
                            System.err.println("GROQ VISION ERROR: " + body);
                            return reactor.core.publisher.Mono.error(
                                    new RuntimeException("Groq Vision API error: " + body));
                        }))
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(60))
                .block();

        return parseGroqResponse(response);
    }

    private String parseGroqResponse(String responseBody) throws Exception{
        JsonNode root=objectMapper.readTree(responseBody);
        return root.path("choices").get(0)
                .path("message").path("content").asText();
    }
}
