package com.inshorts.news.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "article_id", nullable = false)
    private String articleId;

    /**
     * Interaction type: "view", "click", "share"
     * Weights: view=1, click=3, share=5
     */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "user_lat")
    private Double userLat;

    @Column(name = "user_lon")
    private Double userLon;

    @Column(name = "event_time")
    private LocalDateTime eventTime;
}
