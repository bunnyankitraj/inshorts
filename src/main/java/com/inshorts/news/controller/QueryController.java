package com.inshorts.news.controller;

import com.inshorts.news.dto.NewsApiResponse;
import com.inshorts.news.dto.QueryRequest;
import com.inshorts.news.service.QueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * POST /api/v1/query
 *
 * Accepts a natural language query and delegates the full pipeline
 * (LLM analysis → routing → retrieval → enrichment) to QueryService.
 */
@RestController
@RequestMapping("/api/v1/query")
@RequiredArgsConstructor
public class QueryController {

    private final QueryService queryService;

    @PostMapping
    public ResponseEntity<NewsApiResponse> processNaturalLanguageQuery(
            @RequestBody QueryRequest request) {

        if (request.getQuery() == null || request.getQuery().isBlank()) {
            throw new IllegalArgumentException("Query must not be empty");
        }

        return ResponseEntity.ok(queryService.processQuery(request));
    }
}
