package com.inshorts.news.util;

import com.inshorts.news.exception.NewsNotFoundException;
import com.inshorts.news.dto.ArticleResponse;
import com.inshorts.news.dto.NewsApiResponse;

import java.util.List;

/**
 * Utility class for request validation and response building.
 * Centralizes all validation rules so they are not duplicated across controllers.
 */
public class NewsRequestValidator {

    private NewsRequestValidator() {}

    public static void validateLimit(int limit) {
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("limit must be between 1 and 50");
        }
    }

    public static void validateThreshold(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("threshold must be between 0.0 and 1.0");
        }
    }

    public static void validateCoordinates(double lat, double lon) {
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new IllegalArgumentException(
                "Invalid coordinates: lat must be [-90, 90] and lon must be [-180, 180]"
            );
        }
    }

    public static void requireNonEmpty(List<ArticleResponse> articles, String message) {
        if (articles.isEmpty()) {
            throw new NewsNotFoundException(message);
        }
    }

    public static NewsApiResponse buildResponse(
            List<ArticleResponse> articles,
            String endpoint,
            List<String> intent,
            List<String> entities,
            String queryUsed) {
        return NewsApiResponse.builder()
            .metadata(NewsApiResponse.Metadata.builder()
                .totalResults(articles.size())
                .page(1)
                .queryUsed(queryUsed)
                .intent(intent)
                .entities(entities)
                .endpoint(endpoint)
                .build())
            .articles(articles)
            .build();
    }
}
