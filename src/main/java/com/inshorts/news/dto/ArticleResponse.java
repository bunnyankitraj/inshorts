package com.inshorts.news.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleResponse {

    private String title;
    private String description;
    private String url;

    @JsonProperty("publication_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime publicationDate;

    @JsonProperty("source_name")
    private String sourceName;

    private List<String> category;

    @JsonProperty("relevance_score")
    private Double relevanceScore;

    @JsonProperty("llm_summary")
    private String llmSummary;

    private Double latitude;
    private Double longitude;

    /**
     * Only present for "nearby" responses — distance in km from user.
     */
    @JsonProperty("distance_km")
    private Double distanceKm;
}
