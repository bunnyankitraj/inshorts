package com.inshorts.news.service;

import com.inshorts.news.dto.ArticleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnrichmentService {

    private final LLMService llmService;

    @Value("${llm.summary.delay-ms:300}")
    private long summaryDelayMs;

    /**
     * Enriches articles with LLM summaries one-by-one to avoid Gemini rate limits.
     */
    public List<ArticleResponse> enrichWithSummaries(List<ArticleResponse> articles) {
        log.info("Enriching {} articles with LLM summaries...", articles.size());

        for (ArticleResponse article : articles) {
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
            } finally {
                pauseBetweenSummaryCalls();
            }
        }

        log.info("Enrichment complete for {} articles.", articles.size());
        return articles;
    }

    private void pauseBetweenSummaryCalls() {
        if (summaryDelayMs <= 0) {
            return;
        }

        try {
            Thread.sleep(summaryDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String fallbackSummary(String description) {
        return description != null && description.length() > 200
            ? description.substring(0, 200) + "..."
            : description;
    }
}
