package com.inshorts.news.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.inshorts.news.model.Article;
import com.inshorts.news.model.UserEvent;
import com.inshorts.news.repository.ArticleRepository;
import com.inshorts.news.repository.UserEventRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataIngestionService {

    private final ArticleRepository articleRepository;
    private final UserEventRepository userEventRepository;

    @Value("${news.data.file-path}")
    private Resource newsDataFile;

    private static final List<String> EVENT_TYPES = List.of("view", "click", "share");
    private static final Random RANDOM = new Random(42L);

    @PostConstruct
    @Transactional
    public void ingestData() {
        ingestArticles();
        seedUserEvents();
    }

    /**
     * Reads news_data.json and saves all articles to the database.
     * Skips articles that already exist (by ID).
     */
    private void ingestArticles() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());

            List<Map<String, Object>> rawArticles = mapper.readValue(
                newsDataFile.getInputStream(),
                new TypeReference<>() {}
            );

            int saved = 0;
            for (Map<String, Object> raw : rawArticles) {
                String id = (String) raw.get("id");

                // Skip duplicates
                if (articleRepository.existsById(id)) {
                    continue;
                }

                Article article = mapToArticle(raw, mapper);
                articleRepository.save(article);
                saved++;
            }

            log.info("Data ingestion complete: {} articles saved to database.", saved);

        } catch (IOException e) {
            log.error("Failed to ingest news data from file: {}", e.getMessage(), e);
            throw new RuntimeException("Data ingestion failed", e);
        }
    }

    /**
     * Seeds the user_events table with simulated user interaction data.
     * Creates ~300 events tied to existing articles for the trending feed demo.
     */
    private void seedUserEvents() {
        if (userEventRepository.count() > 0) {
            log.info("User events already seeded, skipping.");
            return;
        }

        List<Article> articles = articleRepository.findAll();
        if (articles.isEmpty()) return;

        List<UserEvent> events = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            Article randomArticle = articles.get(RANDOM.nextInt(articles.size()));
            String eventType = EVENT_TYPES.get(RANDOM.nextInt(EVENT_TYPES.size()));

            // Simulate user near the article's location with slight noise
            double userLat = (randomArticle.getLatitude() != null)
                ? randomArticle.getLatitude() + (RANDOM.nextGaussian() * 0.5)
                : 20.0 + RANDOM.nextDouble() * 40;
            double userLon = (randomArticle.getLongitude() != null)
                ? randomArticle.getLongitude() + (RANDOM.nextGaussian() * 0.5)
                : 60.0 + RANDOM.nextDouble() * 80;

            // Events spread over the last 48 hours
            LocalDateTime eventTime = LocalDateTime.now()
                .minusHours(RANDOM.nextInt(48))
                .minusMinutes(RANDOM.nextInt(60));

            events.add(UserEvent.builder()
                .articleId(randomArticle.getId())
                .eventType(eventType)
                .userLat(userLat)
                .userLon(userLon)
                .eventTime(eventTime)
                .build());
        }

        userEventRepository.saveAll(events);
        log.info("Seeded {} simulated user events for trending feed.", events.size());
    }

    @SuppressWarnings("unchecked")
    private Article mapToArticle(Map<String, Object> raw, ObjectMapper mapper) {
        String categoryRaw = "";
        Object catObj = raw.get("category");
        if (catObj instanceof List<?> catList) {
            categoryRaw = String.join(",", (List<String>) catList);
        }

        String pubDateStr = (String) raw.get("publication_date");
        LocalDateTime pubDate = null;
        if (pubDateStr != null) {
            try {
                pubDate = LocalDateTime.parse(pubDateStr);
            } catch (Exception e) {
                log.warn("Could not parse date: {}", pubDateStr);
            }
        }

        Double lat = raw.get("latitude") != null ? ((Number) raw.get("latitude")).doubleValue() : null;
        Double lon = raw.get("longitude") != null ? ((Number) raw.get("longitude")).doubleValue() : null;
        Double score = raw.get("relevance_score") != null ? ((Number) raw.get("relevance_score")).doubleValue() : 0.0;

        Article article = Article.builder()
            .id((String) raw.get("id"))
            .title((String) raw.get("title"))
            .description((String) raw.get("description"))
            .url((String) raw.get("url"))
            .publicationDate(pubDate)
            .sourceName((String) raw.get("source_name"))
            .categoryRaw(categoryRaw)
            .relevanceScore(score)
            .latitude(lat)
            .longitude(lon)
            .build();

        article.syncCategoryFromRaw();
        return article;
    }
}
