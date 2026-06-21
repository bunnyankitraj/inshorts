package com.inshorts.news.service;

import com.inshorts.news.dto.ArticleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnrichmentService {

    private final LLMService llmService;

    /**
     * Enriches a list of articles by generating LLM summaries asynchronously.
     * All summaries are generated in parallel to reduce total latency.
     */
    public List<ArticleResponse> enrichWithSummaries(List<ArticleResponse> articles) {
        log.info("Enriching {} articles with LLM summaries...", articles.size());

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (ArticleResponse article : articles) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    String summary = llmService.generateSummary(
                        article.getTitle(),
                        article.getDescription()
                    );
                    article.setLlmSummary(summary);
                } catch (Exception e) {
                    log.warn("Could not generate summary for article '{}': {}",
                        article.getTitle(), e.getMessage());
                    // Fallback: use truncated description
                    String desc = article.getDescription();
                    article.setLlmSummary(desc != null && desc.length() > 200
                        ? desc.substring(0, 200) + "..."
                        : desc);
                }
            });
            futures.add(future);
        }

        // Wait for all parallel summary calls to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Enrichment complete for {} articles.", articles.size());
        return articles;
    }
}
