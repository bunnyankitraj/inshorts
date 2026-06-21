package com.inshorts.news.dto;

import lombok.*;

import java.util.List;

/**
 * Represents the structured output from the LLM after analyzing a user query.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMAnalysis {

    /**
     * Named entities extracted from the query.
     * Examples: "Elon Musk", "Twitter", "Palo Alto", "New York Times"
     */
    private List<String> entities;

    /**
     * Key concepts / topics from the query.
     * Examples: "acquisition", "technology", "politics"
     */
    private List<String> concepts;

    /**
     * Determined intent — maps to our simulated API endpoints.
     * Valid values: "category", "score", "search", "source", "nearby"
     * Multiple intents are possible (e.g., ["source", "category"])
     */
    private List<String> intent;

    /**
     * A cleaned/normalized search query derived from the user's input.
     * Used for the "search" endpoint text matching.
     */
    private String searchQuery;
}
