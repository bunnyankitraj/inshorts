package com.inshorts.news.dto;

import lombok.Data;

@Data
public class QueryRequest {

    /**
     * Natural language query from the user.
     * Example: "Latest developments near Mumbai"
     */
    private String query;

    /**
     * Optional user location for nearby/trending context.
     */
    private Double userLat;
    private Double userLon;

    /**
     * Optional radius in km for nearby search (default: 10km).
     */
    private Double radiusKm;

    /**
     * Max results per page (default: 5).
     */
    private Integer limit = 5;

    /**
     * 1-based page number (default: 1).
     */
    private Integer page = 1;
}
