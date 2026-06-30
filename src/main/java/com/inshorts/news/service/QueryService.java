package com.inshorts.news.service;

import com.inshorts.news.dto.ArticleResponse;
import com.inshorts.news.dto.LLMAnalysis;
import com.inshorts.news.dto.NewsApiResponse;
import com.inshorts.news.dto.QueryRequest;
import com.inshorts.news.exception.NewsNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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
        int page = request.getPage() != null ? request.getPage() : 1;

        // Step 1: LLM analysis
        LLMAnalysis analysis = llmService.analyzeQuery(request.getQuery());
        log.info("LLM Analysis — intent: {}, entities: {}", analysis.getIntent(), analysis.getEntities());

        // Step 2: Route to correct retrieval strategy
        Page<ArticleResponse> result = routeToEndpoint(analysis, request, page, limit);

        if (result.isEmpty()) {
            throw new NewsNotFoundException(
                "No articles found for your query: '" + request.getQuery() + "'"
            );
        }

        // Step 3: Enrich with LLM summaries (parallel)
        List<ArticleResponse> enriched = enrichmentService.enrichWithSummaries(result.getContent());

        // Step 4: Build structured response
        return buildResponse(enriched, result, analysis, request, request.getQuery());
    }

    /**
     * Routes to the correct retrieval strategy based on LLM-detected intent.
     * Priority: nearby > source > category > score > search (default)
     */
    private Page<ArticleResponse> routeToEndpoint(LLMAnalysis analysis, QueryRequest request, int page, int limit) {
        List<String> intent = analysis.getIntent();

        if (intent.contains("nearby") && request.getUserLat() != null && request.getUserLon() != null) {
            double radius = request.getRadiusKm() != null ? request.getRadiusKm() : 10.0;
            log.info("Routing to NEARBY — radius={}km", radius);
            return newsService.getNearby(request.getUserLat(), request.getUserLon(), radius, page, limit);
        }

        if (intent.contains("source") && !analysis.getEntities().isEmpty()) {
            String source = analysis.getEntities().get(0);
            log.info("Routing to SOURCE — source={}", source);
            return newsService.getBySource(source, page, limit);
        }

        if (intent.contains("category")) {
            String category = !analysis.getEntities().isEmpty()
                ? analysis.getEntities().get(0)
                : (!analysis.getConcepts().isEmpty() ? analysis.getConcepts().get(0) : "General");
            log.info("Routing to CATEGORY — category={}", category);
            return newsService.getByCategory(category, page, limit);
        }

        if (intent.contains("score")) {
            log.info("Routing to SCORE");
            return newsService.getByScore(0.7, page, limit);
        }

        log.info("Routing to SEARCH — query={}", analysis.getSearchQuery());
        return newsService.getBySearch(analysis.getSearchQuery(), page, limit);
    }

    private NewsApiResponse buildResponse(List<ArticleResponse> articles, Page<?> result, LLMAnalysis analysis,
                                          QueryRequest request, String originalQuery) {
        return NewsApiResponse.builder()
            .metadata(NewsApiResponse.Metadata.builder()
                .totalResults(result.getTotalElements())
                .page(result.getNumber() + 1)
                .pageSize(result.getSize())
                .totalPages(result.getTotalPages())
                .queryUsed(originalQuery)
                .intent(analysis.getIntent())
                .entities(analysis.getEntities())
                .endpoint(resolveEndpoint(analysis, request))
                .build())
            .articles(articles)
            .build();
    }

    /**
     * Determines the actual endpoint used, mirroring the routing priority in routeToEndpoint.
     */
    private String resolveEndpoint(LLMAnalysis analysis, QueryRequest request) {
        List<String> intent = analysis.getIntent();
        if (intent == null || intent.isEmpty()) return "search";
        if (intent.contains("nearby") && request.getUserLat() != null && request.getUserLon() != null) return "nearby";
        if (intent.contains("source")) return "source";
        if (intent.contains("category")) return "category";
        if (intent.contains("score")) return "score";
        return "search";
    }
}
