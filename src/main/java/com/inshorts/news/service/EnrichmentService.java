package com.inshorts.news.service;

import com.inshorts.news.dto.ArticleResponse;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class EnrichmentService {

    private final LLMService llmService;
    private final ExecutorService executor;
    private final long summaryTimeoutMs;

    public EnrichmentService(
            LLMService llmService,
            @Value("${llm.enrichment.pool-size:8}") int poolSize,
            @Value("${llm.enrichment.timeout-ms:8000}") long summaryTimeoutMs) {
        this.llmService = llmService;
        this.summaryTimeoutMs = summaryTimeoutMs;
        // Dedicated bounded pool: blocking LLM calls must NOT run on the shared
        // ForkJoinPool.commonPool (used by parallelStream), which would starve
        // every other parallel task in the JVM under load.
        this.executor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "llm-enrich");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Enriches articles with LLM summaries concurrently on a bounded pool.
     * Each summary has its own timeout and falls back to a truncated description
     * on failure, so one slow/failed call never blocks the whole response.
     */
    public List<ArticleResponse> enrichWithSummaries(List<ArticleResponse> articles) {
        if (articles == null || articles.isEmpty()) {
            return articles;
        }
        log.info("Enriching {} articles with LLM summaries...", articles.size());

        List<CompletableFuture<Void>> futures = articles.stream()
            .map(article -> CompletableFuture
                .runAsync(() -> article.setLlmSummary(
                    llmService.generateSummary(article.getTitle(), article.getDescription())), executor)
                .orTimeout(summaryTimeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    log.warn("Could not generate summary for article '{}': {}",
                        article.getTitle(), ex.getMessage());
                    article.setLlmSummary(fallbackSummary(article.getDescription()));
                    return null;
                }))
            .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Enrichment complete for {} articles.", articles.size());
        return articles;
    }

    private String fallbackSummary(String description) {
        return description != null && description.length() > 200
            ? description.substring(0, 200) + "..."
            : description;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
