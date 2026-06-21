package com.inshorts.news.service;

import com.inshorts.news.dto.ArticleResponse;
import com.inshorts.news.dto.LLMAnalysis;
import com.inshorts.news.dto.NewsApiResponse;
import com.inshorts.news.dto.QueryRequest;
import com.inshorts.news.exception.NewsNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates the full LLM-driven query pipeline:
 *   1. Analyze query using LLM → extract entities + intent
 *   2. Route to the correct retrieval strategy
 *   3. Enrich results with LLM summaries
 *   4. Build and return a structured response
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueryService {

    private final LLMService llmService;
    private final NewsService newsService;
    private final EnrichmentService enrichmentService;

    public NewsApiResponse processQuery(QueryRequest request) {
        log.info("Processing natural language query: '{}'", request.getQuery());

        int limit = request.getLimit() != null ? request.getLimit() : 5;

        // Step 1: LLM analysis
        LLMAnalysis analysis = llmService.analyzeQuery(request.getQuery());
        log.info("LLM Analysis — intent: {}, entities: {}", analysis.getIntent(), analysis.getEntities());

        // Step 2: Route to correct retrieval strategy
        List<ArticleResponse> articles = routeToEndpoint(analysis, request, limit);

        if (articles.isEmpty()) {
            throw new NewsNotFoundException(
                "No articles found for your query: '" + request.getQuery() + "'"
            );
        }

        // Step 3: Enrich with LLM summaries (parallel)
        List<ArticleResponse> enriched = enrichmentService.enrichWithSummaries(articles);

        // Step 4: Build structured response
        return buildResponse(enriched, analysis, request.getQuery());
    }

    /**
     * Routes to the correct retrieval strategy based on primary intent.
     * Priority: nearby > source > category > score > search (default)
     */
    private List<ArticleResponse> routeToEndpoint(LLMAnalysis analysis, QueryRequest request, int limit) {
        List<String> intent = analysis.getIntent();

        if (shouldRouteToNearby(intent, request)) {
            analysis.setIntent(List.of("nearby"));
            double radius = request.getRadiusKm() != null ? request.getRadiusKm() : 10.0;
            log.info("Routing to NEARBY — radius={}km", radius);
            return newsService.getNearby(request.getUserLat(), request.getUserLon(), radius, limit);
        }

        if (intent.contains("source") && !analysis.getEntities().isEmpty()) {
            String source = analysis.getEntities().get(0);
            log.info("Routing to SOURCE — source={}", source);
            return newsService.getBySource(source, limit);
        }

        if (intent.contains("category")) {
            String category = !analysis.getEntities().isEmpty()
                ? analysis.getEntities().get(0)
                : (!analysis.getConcepts().isEmpty() ? analysis.getConcepts().get(0) : "General");
            log.info("Routing to CATEGORY — category={}", category);
            return newsService.getByCategory(category, limit);
        }

        if (intent.contains("score")) {
            log.info("Routing to SCORE");
            return newsService.getByScore(0.7, limit);
        }

        log.info("Routing to SEARCH — query={}", analysis.getSearchQuery());
        return newsService.getBySearch(analysis.getSearchQuery(), limit);
    }

    private boolean shouldRouteToNearby(List<String> intent, QueryRequest request) {
        if (request.getUserLat() == null || request.getUserLon() == null) {
            return false;
        }
        if (intent.contains("nearby")) {
            return true;
        }

        String query = request.getQuery() == null ? "" : request.getQuery().toLowerCase();
        return query.contains(" near ")
            || query.startsWith("near ")
            || query.contains(" nearby")
            || query.contains(" around ")
            || query.contains(" local ");
    }

    private NewsApiResponse buildResponse(List<ArticleResponse> articles, LLMAnalysis analysis, String originalQuery) {
        return NewsApiResponse.builder()
            .metadata(NewsApiResponse.Metadata.builder()
                .totalResults(articles.size())
                .page(1)
                .queryUsed(originalQuery)
                .intent(analysis.getIntent())
                .entities(analysis.getEntities())
                .endpoint(analysis.getIntent() != null && !analysis.getIntent().isEmpty()
                    ? analysis.getIntent().get(0) : "search")
                .build())
            .articles(articles)
            .build();
    }
}
