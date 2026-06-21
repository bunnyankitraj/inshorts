package com.inshorts.news.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inshorts.news.dto.LLMAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LLMService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${llm.provider}")
    private String llmProvider;

    @Value("${llm.gemini.api-key}")
    private String geminiApiKey;

    @Value("${llm.gemini.model}")
    private String geminiModel;

    @Value("${llm.gemini.base-url}")
    private String geminiBaseUrl;

    @Value("${llm.openai.api-key}")
    private String openaiApiKey;

    @Value("${llm.openai.model}")
    private String openaiModel;

    @Value("${llm.openai.base-url}")
    private String openaiBaseUrl;

    public LLMService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    // =========================================================================
    // Query Analysis — extracts entities, concepts, and intent from user query
    // =========================================================================

    /**
     * Analyzes a user's natural language query and returns structured entities + intent.
     */
    public LLMAnalysis analyzeQuery(String userQuery) {
        String prompt = buildQueryAnalysisPrompt(userQuery);
        try {
            String rawResponse = callLLM(prompt);
            return parseQueryAnalysisResponse(rawResponse, userQuery);
        } catch (Exception e) {
            log.warn("LLM query analysis failed, falling back to search intent. Error: {}", e.getMessage());
            return LLMAnalysis.builder()
                .entities(List.of())
                .concepts(List.of())
                .intent(List.of("search"))
                .searchQuery(userQuery)
                .build();
        }
    }

    /**
     * Generates a concise summary of a news article using the LLM.
     */
    public String generateSummary(String title, String description) {
        String prompt = String.format(
            "You are a news editor. Write a concise 2-sentence summary of this news article.\n\n" +
            "Title: %s\n\nDescription: %s\n\n" +
            "Respond with ONLY the 2-sentence summary, no extra text.",
            title, description
        );
        try {
            return callLLM(prompt).trim();
        } catch (Exception e) {
            log.warn("LLM summary generation failed: {}", e.getMessage());
            return description != null && description.length() > 150
                ? description.substring(0, 150) + "..."
                : description;
        }
    }

    // =========================================================================
    // LLM Routing
    // =========================================================================

    private String callLLM(String prompt) {
        if ("openai".equalsIgnoreCase(llmProvider)) {
            return callOpenAI(prompt);
        } else {
            return callGemini(prompt);
        }
    }

    // =========================================================================
    // Google Gemini Integration
    // =========================================================================

    private String callGemini(String prompt) {
        String url = String.format("%s/models/%s:generateContent?key=%s",
            geminiBaseUrl, geminiModel, geminiApiKey);

        Map<String, Object> requestBody = Map.of(
            "contents", List.of(Map.of(
                "parts", List.of(Map.of("text", prompt))
            )),
            "generationConfig", Map.of(
                "temperature", 0.1,
                "maxOutputTokens", 512
            )
        );

        String responseBody = webClient.post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(String.class)
            .block();

        return extractGeminiText(responseBody);
    }

    private String extractGeminiText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("candidates")
                .get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText();
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", responseBody);
            throw new RuntimeException("Failed to parse Gemini response", e);
        }
    }

    // =========================================================================
    // OpenAI Integration
    // =========================================================================

    private String callOpenAI(String prompt) {
        String url = openaiBaseUrl + "/chat/completions";

        Map<String, Object> requestBody = Map.of(
            "model", openaiModel,
            "messages", List.of(Map.of(
                "role", "user",
                "content", prompt
            )),
            "temperature", 0.1,
            "max_tokens", 512
        );

        String responseBody = webClient.post()
            .uri(url)
            .header("Authorization", "Bearer " + openaiApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(String.class)
            .block();

        return extractOpenAIText(responseBody);
    }

    private String extractOpenAIText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText();
        } catch (Exception e) {
            log.error("Failed to parse OpenAI response: {}", responseBody);
            throw new RuntimeException("Failed to parse OpenAI response", e);
        }
    }

    // =========================================================================
    // Prompt Engineering
    // =========================================================================

    private String buildQueryAnalysisPrompt(String userQuery) {
        return String.format("""
            You are a news search engine query analyzer. Analyze the following user query and extract structured information.
            
            User Query: "%s"
            
            Instructions:
            1. Extract named entities (people, organizations, locations, companies).
            2. Extract key concepts and topics.
            3. Determine the search intent. Intent must be one or more from this exact list:
               - "category" → user wants news from a specific category (Technology, Sports, Business, Health, Science, World, Politics, General)
               - "score" → user wants highly relevant/top-ranked articles
               - "search" → user wants to search by keywords or topic
               - "source" → user wants news from a specific source (Reuters, BBC, TechCrunch, etc.)
               - "nearby" → user mentions a location or wants news near a place
            4. Generate a clean search query string from the user's input.
            
            Respond ONLY with valid JSON in this exact format (no markdown, no explanation):
            {
              "entities": ["entity1", "entity2"],
              "concepts": ["concept1", "concept2"],
              "intent": ["intent1", "intent2"],
              "searchQuery": "cleaned search query"
            }
            """, userQuery);
    }

    private LLMAnalysis parseQueryAnalysisResponse(String rawResponse, String fallbackQuery) {
        try {
            // Strip markdown code blocks if present
            String cleaned = rawResponse.trim()
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

            JsonNode node = objectMapper.readTree(cleaned);

            List<String> entities = new ArrayList<>();
            List<String> concepts = new ArrayList<>();
            List<String> intent = new ArrayList<>();

            node.path("entities").forEach(e -> entities.add(e.asText()));
            node.path("concepts").forEach(c -> concepts.add(c.asText()));
            node.path("intent").forEach(i -> intent.add(i.asText()));

            String searchQuery = node.path("searchQuery").asText(fallbackQuery);

            // Default to "search" if no intent determined
            if (intent.isEmpty()) {
                intent.add("search");
            }

            return LLMAnalysis.builder()
                .entities(entities)
                .concepts(concepts)
                .intent(intent)
                .searchQuery(searchQuery)
                .build();

        } catch (JsonProcessingException e) {
            log.warn("Could not parse LLM JSON response, falling back. Raw: {}", rawResponse);
            return LLMAnalysis.builder()
                .entities(List.of())
                .concepts(List.of())
                .intent(List.of("search"))
                .searchQuery(fallbackQuery)
                .build();
        }
    }
}
