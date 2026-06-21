package com.inshorts.news.service;

import com.inshorts.news.dto.ArticleResponse;
import com.inshorts.news.model.Article;
import com.inshorts.news.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {

    private final ArticleRepository articleRepository;

    @Value("${nearby.default-radius.km:10}")
    private double defaultRadiusKm;

    // =========================================================================
    // 1. Category — rank by publication_date DESC
    // =========================================================================
    public List<ArticleResponse> getByCategory(String category, int limit) {
        log.info("Fetching articles by category: {}", category);
        return articleRepository.findByCategory(category)
            .stream()
            .limit(limit)
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    // =========================================================================
    // 2. Score — filter score >= threshold, rank by score DESC
    // =========================================================================
    public List<ArticleResponse> getByScore(double threshold, int limit) {
        log.info("Fetching articles with score >= {}", threshold);
        return articleRepository.findByScoreAboveThreshold(threshold)
            .stream()
            .limit(limit)
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    // =========================================================================
    // 3. Search — text search in title + description
    //    Rank by: 0.5 * relevance_score + 0.5 * text_match_score
    // =========================================================================
    public List<ArticleResponse> getBySearch(String query, int limit) {
        log.info("Performing text search for: {}", query);
        List<Article> candidates = articleRepository.findByTextSearch(query);

        String queryLower = query.toLowerCase();
        return candidates.stream()
            .map(a -> {
                double textScore = computeTextMatchScore(a, queryLower);
                double combinedScore = 0.5 * safeScore(a.getRelevanceScore()) + 0.5 * textScore;
                ArticleResponse resp = toResponse(a);
                resp.setRelevanceScore(Math.round(combinedScore * 100.0) / 100.0);
                return resp;
            })
            .sorted(Comparator.comparingDouble(ArticleResponse::getRelevanceScore).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    // =========================================================================
    // 4. Source — filter by source name, rank by publication_date DESC
    // =========================================================================
    public List<ArticleResponse> getBySource(String source, int limit) {
        log.info("Fetching articles from source: {}", source);
        return articleRepository.findBySource(source)
            .stream()
            .limit(limit)
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    // =========================================================================
    // 5. Nearby — Haversine distance filter, rank by distance ASC
    // =========================================================================
    public List<ArticleResponse> getNearby(double lat, double lon, double radiusKm, int limit) {
        log.info("Fetching nearby articles: lat={}, lon={}, radius={}km", lat, lon, radiusKm);
        double effectiveRadius = radiusKm > 0 ? radiusKm : defaultRadiusKm;

        return articleRepository.findAllWithCoordinates()
            .stream()
            .map(a -> {
                double distanceKm = haversineDistance(lat, lon, a.getLatitude(), a.getLongitude());
                return new ArticleWithDistance(a, distanceKm);
            })
            .filter(ad -> ad.distanceKm <= effectiveRadius)
            .sorted(Comparator.comparingDouble(ad -> ad.distanceKm))
            .limit(limit)
            .map(ad -> {
                ArticleResponse resp = toResponse(ad.article);
                resp.setDistanceKm(Math.round(ad.distanceKm * 100.0) / 100.0);
                return resp;
            })
            .collect(Collectors.toList());
    }

    // =========================================================================
    // Mapping
    // =========================================================================
    public ArticleResponse toResponse(Article article) {
        article.syncCategoryFromRaw();
        return ArticleResponse.builder()
            .title(article.getTitle())
            .description(article.getDescription())
            .url(article.getUrl())
            .publicationDate(article.getPublicationDate())
            .sourceName(article.getSourceName())
            .category(article.getCategory())
            .relevanceScore(article.getRelevanceScore())
            .latitude(article.getLatitude())
            .longitude(article.getLongitude())
            .build();
    }

    // =========================================================================
    // Haversine Formula — returns distance in km between two lat/lon points
    // =========================================================================
    public double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double EARTH_RADIUS_KM = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private double computeTextMatchScore(Article article, String queryLower) {
        String titleLower = article.getTitle() != null ? article.getTitle().toLowerCase() : "";
        String descLower = article.getDescription() != null ? article.getDescription().toLowerCase() : "";

        String[] terms = queryLower.split("\\s+");
        long titleMatches = 0, descMatches = 0;

        for (String term : terms) {
            if (term.length() >= 3) {
                if (titleLower.contains(term)) titleMatches++;
                if (descLower.contains(term)) descMatches++;
            }
        }

        int totalTerms = terms.length;
        if (totalTerms == 0) return 0.0;

        // Title matches worth more than description
        return Math.min(1.0, (titleMatches * 1.5 + descMatches * 0.5) / totalTerms);
    }

    private double safeScore(Double score) {
        return score != null ? score : 0.0;
    }

    // Internal record for nearby search
    private record ArticleWithDistance(Article article, double distanceKm) {}
}
