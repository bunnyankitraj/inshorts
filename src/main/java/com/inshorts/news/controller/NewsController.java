package com.inshorts.news.controller;

import com.inshorts.news.dto.ArticleResponse;
import com.inshorts.news.dto.NewsApiResponse;
import com.inshorts.news.service.EnrichmentService;
import com.inshorts.news.service.NewsService;
import com.inshorts.news.util.NewsRequestValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Direct news retrieval endpoints.
 * All validation, response building, and business logic lives in service/util layers.
 *
 * GET /api/v1/news/category
 * GET /api/v1/news/score
 * GET /api/v1/news/search
 * GET /api/v1/news/source
 * GET /api/v1/news/nearby
 */
@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
@Slf4j
public class NewsController {

    private final NewsService newsService;
    private final EnrichmentService enrichmentService;

    @GetMapping("/category")
    public ResponseEntity<NewsApiResponse> getByCategory(
            @RequestParam String category,
            @RequestParam(defaultValue = "5") int limit) {

        NewsRequestValidator.validateLimit(limit);

        List<ArticleResponse> articles = enrichmentService.enrichWithSummaries(
            newsService.getByCategory(category, limit)
        );

        NewsRequestValidator.requireNonEmpty(articles, "No articles found for category: " + category);

        return ResponseEntity.ok(
            NewsRequestValidator.buildResponse(articles, "category", List.of("category"), List.of(category), category)
        );
    }

    @GetMapping("/score")
    public ResponseEntity<NewsApiResponse> getByScore(
            @RequestParam(defaultValue = "0.7") double threshold,
            @RequestParam(defaultValue = "5") int limit) {

        NewsRequestValidator.validateThreshold(threshold);
        NewsRequestValidator.validateLimit(limit);

        List<ArticleResponse> articles = enrichmentService.enrichWithSummaries(
            newsService.getByScore(threshold, limit)
        );

        NewsRequestValidator.requireNonEmpty(articles, "No articles found with score above: " + threshold);

        return ResponseEntity.ok(
            NewsRequestValidator.buildResponse(articles, "score", List.of("score"), List.of(), "relevance >= " + threshold)
        );
    }

    @GetMapping("/search")
    public ResponseEntity<NewsApiResponse> searchArticles(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query parameter must not be empty");
        }
        NewsRequestValidator.validateLimit(limit);

        List<ArticleResponse> articles = enrichmentService.enrichWithSummaries(
            newsService.getBySearch(query, limit)
        );

        NewsRequestValidator.requireNonEmpty(articles, "No articles found matching: " + query);

        return ResponseEntity.ok(
            NewsRequestValidator.buildResponse(articles, "search", List.of("search"), List.of(), query)
        );
    }

    @GetMapping("/source")
    public ResponseEntity<NewsApiResponse> getBySource(
            @RequestParam String source,
            @RequestParam(defaultValue = "5") int limit) {

        NewsRequestValidator.validateLimit(limit);

        List<ArticleResponse> articles = enrichmentService.enrichWithSummaries(
            newsService.getBySource(source, limit)
        );

        NewsRequestValidator.requireNonEmpty(articles, "No articles found from source: " + source);

        return ResponseEntity.ok(
            NewsRequestValidator.buildResponse(articles, "source", List.of("source"), List.of(source), source)
        );
    }

    @GetMapping("/nearby")
    public ResponseEntity<NewsApiResponse> getNearby(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "10") double radius,
            @RequestParam(defaultValue = "5") int limit) {

        NewsRequestValidator.validateCoordinates(lat, lon);
        NewsRequestValidator.validateLimit(limit);

        List<ArticleResponse> articles = enrichmentService.enrichWithSummaries(
            newsService.getNearby(lat, lon, radius, limit)
        );

        NewsRequestValidator.requireNonEmpty(articles,
            String.format("No articles found within %.1fkm of (%.4f, %.4f)", radius, lat, lon));

        return ResponseEntity.ok(
            NewsRequestValidator.buildResponse(articles, "nearby", List.of("nearby"), List.of(),
                String.format("lat=%.4f, lon=%.4f, radius=%.1fkm", lat, lon, radius))
        );
    }
}
