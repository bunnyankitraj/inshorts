package com.inshorts.news.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "articles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Article {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "url", columnDefinition = "TEXT")
    private String url;

    @Column(name = "publication_date")
    private LocalDateTime publicationDate;

    @Column(name = "source_name")
    private String sourceName;

    /**
     * Stored as a comma-separated string for H2 compatibility.
     */
    @Column(name = "category", columnDefinition = "TEXT")
    private String categoryRaw; // e.g. "Technology,Business"

    @Transient
    private List<String> category;

    @Column(name = "relevance_score")
    private Double relevanceScore;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @PostLoad
    @PostPersist
    public void syncCategoryFromRaw() {
        if (categoryRaw != null && !categoryRaw.isBlank()) {
            this.category = List.of(categoryRaw.split(","));
        } else {
            this.category = List.of();
        }
    }

}
