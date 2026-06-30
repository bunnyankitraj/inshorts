package com.inshorts.news.controller;

import com.inshorts.news.dto.ArticleResponse;
import com.inshorts.news.dto.NewsApiResponse;
import com.inshorts.news.service.TrendingService;
import com.inshorts.news.util.NewsRequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GET /api/v1/trending
 *
 * Returns trending articles near the user's location.
 * Caching, scoring, and LLM enrichment are fully handled by TrendingService
 * (enrichment runs inside the cached method, so cache hits are LLM-free).
 */
@RestController
@RequestMapping("/api/v1/trending")
@RequiredArgsConstructor
public class TrendingController {

    private final TrendingService trendingService;

    @GetMapping
    public ResponseEntity<NewsApiResponse> getTrending(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "10") int limit) {

        NewsRequestValidator.validateCoordinates(lat, lon);
        NewsRequestValidator.validateLimit(limit);

        String cacheKey = trendingService.buildCacheKey(lat, lon);

        List<ArticleResponse> articles =
            trendingService.getTrendingByLocation(lat, lon, limit, cacheKey);

        NewsRequestValidator.requireNonEmpty(articles,
            String.format("No trending articles found near (%.4f, %.4f)", lat, lon));

        return ResponseEntity.ok(
            NewsRequestValidator.buildResponse(articles, "trending",
                List.of("nearby", "score"), List.of(),
                String.format("Trending near lat=%.4f, lon=%.4f", lat, lon))
        );
    }
}
