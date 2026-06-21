package com.inshorts.news.service;

import com.inshorts.news.dto.ArticleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnrichmentService {

    private final LLMService llmService;

    /**
     * Enriches articles with LLM summaries in parallel for maximum speed.
     */
    public List<ArticleResponse> enrichWithSummaries(List<ArticleResponse> articles) {
        log.info("Enriching {} articles with LLM summaries (in parallel)...", articles.size());

        articles.parallelStream().forEach(article -> {
            try {
                String summary = llmService.generateSummary(
                    article.getTitle(),
                    article.getDescription()
                );
                article.setLlmSummary(summary);
            } catch (Exception e) {
                log.warn("Could not generate summary for article '{}': {}",
                    article.getTitle(), e.getMessage());
                article.setLlmSummary(fallbackSummary(article.getDescription()));
            }
        });

        log.info("Enrichment complete for {} articles.", articles.size());
        return articles;
    }

    private String fallbackSummary(String description) {
        return description != null && description.length() > 200
            ? description.substring(0, 200) + "..."
            : description;
    }
}
