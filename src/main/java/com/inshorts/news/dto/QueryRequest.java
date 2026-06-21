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
     * Max results to return (default: 5).
     */
    private Integer limit = 5;
}
