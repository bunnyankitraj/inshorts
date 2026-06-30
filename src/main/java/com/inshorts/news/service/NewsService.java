package com.inshorts.news.service;

import com.inshorts.news.dto.ArticleResponse;
import com.inshorts.news.model.Article;
import com.inshorts.news.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Value("${news.search.candidate-cap:100}")
    private int searchCandidateCap;

    // =========================================================================
    // 1. Category — rank by publication_date DESC (paged in SQL)
    // =========================================================================
    @Transactional(readOnly = true)
    public Page<ArticleResponse> getByCategory(String category, int page, int limit) {
        log.info("Fetching articles by category: {} (page={}, limit={})", category, page, limit);
        return articleRepository.findByCategory(category, PageRequest.of(page - 1, limit))
            .map(this::toResponse);
    }

    // =========================================================================
    // 2. Score — filter score >= threshold, rank by score DESC (paged in SQL)
    // =========================================================================
    @Transactional(readOnly = true)
    public Page<ArticleResponse> getByScore(double threshold, int page, int limit) {
        log.info("Fetching articles with score >= {} (page={}, limit={})", threshold, page, limit);
        return articleRepository.findByScoreAboveThreshold(threshold, PageRequest.of(page - 1, limit))
            .map(this::toResponse);
    }

    // =========================================================================
    // 3. Search — text search in title + description
    //    Rank by: 0.5 * relevance_score + 0.5 * text_match_score
    //    Pull a capped candidate set from the DB, re-rank, then page in memory.
    // =========================================================================
    @Transactional(readOnly = true)
    public Page<ArticleResponse> getBySearch(String query, int page, int limit) {
        log.info("Performing text search for: {} (page={}, limit={})", query, page, limit);
        int cap = Math.max(searchCandidateCap, page * limit);
        List<Article> candidates = articleRepository.findByTextSearch(query, PageRequest.of(0, cap));

        String queryLower = query.toLowerCase();
        List<ArticleResponse> ranked = candidates.stream()
            .map(a -> {
                double textScore = computeTextMatchScore(a, queryLower);
                double combinedScore = 0.5 * safeScore(a.getRelevanceScore()) + 0.5 * textScore;
                ArticleResponse resp = toResponse(a);
                resp.setRelevanceScore(Math.round(combinedScore * 100.0) / 100.0);
                return resp;
            })
            .sorted(Comparator.comparingDouble(ArticleResponse::getRelevanceScore).reversed())
            .collect(Collectors.toList());

        return paginate(ranked, page, limit);
    }

    // =========================================================================
    // 4. Source — filter by source name, rank by publication_date DESC (paged in SQL)
    // =========================================================================
    @Transactional(readOnly = true)
    public Page<ArticleResponse> getBySource(String source, int page, int limit) {
        log.info("Fetching articles from source: {} (page={}, limit={})", source, page, limit);
        return articleRepository.findBySource(source, PageRequest.of(page - 1, limit))
            .map(this::toResponse);
    }

    // =========================================================================
    // 5. Nearby — Haversine distance filter, rank by distance ASC, paged in memory
    // =========================================================================
    @Transactional(readOnly = true)
    public Page<ArticleResponse> getNearby(double lat, double lon, double radiusKm, int page, int limit) {
        log.info("Fetching nearby articles: lat={}, lon={}, radius={}km (page={}, limit={})",
            lat, lon, radiusKm, page, limit);
        double effectiveRadius = radiusKm > 0 ? radiusKm : defaultRadiusKm;

        // Step 1: Bounding box pre-filter in SQL (cheap, index-friendly)
        double latDelta = effectiveRadius / 111.0;
        double lonDelta = effectiveRadius / (111.0 * Math.cos(Math.toRadians(lat)));
        List<Article> candidates = articleRepository.findWithinBoundingBox(
            lat - latDelta, lat + latDelta,
            lon - lonDelta, lon + lonDelta
        );

        // Step 2: Exact Haversine filter + sort in Java (on small candidate set)
        List<ArticleResponse> within = candidates.stream()
            .map(a -> new ArticleWithDistance(a, haversineDistance(lat, lon, a.getLatitude(), a.getLongitude())))
            .filter(ad -> ad.distanceKm <= effectiveRadius)
            .sorted(Comparator.comparingDouble(ad -> ad.distanceKm))
            .map(ad -> {
                ArticleResponse resp = toResponse(ad.article);
                resp.setDistanceKm(Math.round(ad.distanceKm * 100.0) / 100.0);
                return resp;
            })
            .collect(Collectors.toList());

        return paginate(within, page, limit);
    }

    // =========================================================================
    // In-memory pagination for results ranked/filtered in Java (search, nearby).
    // Preserves the total count so metadata reports accurate totals.
    // =========================================================================
    private Page<ArticleResponse> paginate(List<ArticleResponse> all, int page, int limit) {
        Pageable pageable = PageRequest.of(page - 1, limit);
        int from = Math.min((int) pageable.getOffset(), all.size());
        int to = Math.min(from + limit, all.size());
        return new PageImpl<>(all.subList(from, to), pageable, all.size());
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
        boolean titleMatch = article.getTitle() != null && article.getTitle().toLowerCase().contains(queryLower);
        boolean descMatch = article.getDescription() != null && article.getDescription().toLowerCase().contains(queryLower);
        return titleMatch ? 1.0 : (descMatch ? 0.5 : 0.0);
    }

    private double safeScore(Double score) {
        return score != null ? score : 0.0;
    }

    // Internal record for nearby search
    private record ArticleWithDistance(Article article, double distanceKm) {}
}
