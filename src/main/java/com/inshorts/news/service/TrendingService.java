package com.inshorts.news.service;

import com.inshorts.news.dto.ArticleResponse;
import com.inshorts.news.model.Article;
import com.inshorts.news.model.UserEvent;
import com.inshorts.news.repository.ArticleRepository;
import com.inshorts.news.repository.UserEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrendingService {

    private final UserEventRepository userEventRepository;
    private final ArticleRepository articleRepository;
    private final NewsService newsService;

    @Value("${trending.radius.km:50}")
    private double trendingRadiusKm;

    @Value("${trending.max-results:10}")
    private int maxResults;

    // Event interaction weights
    private static final Map<String, Double> EVENT_WEIGHTS = Map.of(
        "view", 1.0,
        "click", 3.0,
        "share", 5.0
    );

    /**
     * Returns trending articles near a given location.
     *
     * Cache key is based on rounded lat/lon (2 decimal places = ~1.1km grid).
     * This simulates geospatial cluster-based caching.
     *
     * Trending Score Formula:
     *   score = SUM[ event_weight * recency_decay(event_time) ] * geo_proximity_boost
     *
     * recency_decay = 1 / (1 + hours_since_event)
     * geo_proximity_boost = 1 + (1 / (1 + distance_from_article_to_user_km))
     */
    @Cacheable(value = "trendingFeed", key = "#cacheKey")
    public List<ArticleResponse> getTrendingByLocation(double lat, double lon, int limit, String cacheKey) {
        log.info("Computing trending feed for cluster key: {} (lat={}, lon={})", cacheKey, lat, lon);
        int effectiveLimit = Math.min(limit, maxResults);

        // Fetch events from last 48 hours
        LocalDateTime since = LocalDateTime.now().minusHours(48);
        List<UserEvent> recentEvents = userEventRepository.findRecentEvents(since);

        if (recentEvents.isEmpty()) {
            log.warn("No recent user events found. Returning top-scored articles as fallback.");
            return articleRepository.findByScoreAboveThreshold(0.7)
                .stream()
                .filter(article -> distanceFromUser(article, lat, lon) <= trendingRadiusKm)
                .limit(effectiveLimit)
                .map(article -> {
                    ArticleResponse resp = newsService.toResponse(article);
                    double dist = distanceFromUser(article, lat, lon);
                    resp.setDistanceKm(Math.round(dist * 100.0) / 100.0);
                    return resp;
                })
                .collect(Collectors.toList());
        }

        // Compute per-article trending scores
        Map<String, Double> articleScores = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        for (UserEvent event : recentEvents) {
            double weight = EVENT_WEIGHTS.getOrDefault(event.getEventType(), 1.0);
            long hoursAgo = ChronoUnit.HOURS.between(event.getEventTime(), now);
            double recencyDecay = 1.0 / (1.0 + hoursAgo);
            double rawScore = weight * recencyDecay;

            articleScores.merge(event.getArticleId(), rawScore, Double::sum);
        }

        // Fetch articles, apply geo proximity boost
        List<Article> allArticles = articleRepository.findAllById(articleScores.keySet());
        Map<String, Article> articleMap = allArticles.stream()
            .collect(Collectors.toMap(Article::getId, a -> a));

        return articleScores.entrySet().stream()
            .filter(e -> articleMap.containsKey(e.getKey()))
            .map(e -> {
                Article article = articleMap.get(e.getKey());
                double baseScore = e.getValue();
                double distanceKm = distanceFromUser(article, lat, lon);
                double geoBoost = computeGeoProximityBoost(distanceKm);
                double trendingScore = baseScore * geoBoost;
                return new ArticleWithScore(article, trendingScore, distanceKm);
            })
            .filter(as -> as.distanceKm() <= trendingRadiusKm)
            .sorted(Comparator.comparingDouble(ArticleWithScore::score).reversed())
            .limit(effectiveLimit)
            .map(as -> {
                ArticleResponse resp = newsService.toResponse(as.article());
                resp.setRelevanceScore(Math.round(as.score() * 100.0) / 100.0);
                resp.setDistanceKm(Math.round(as.distanceKm() * 100.0) / 100.0);
                return resp;
            })
            .collect(Collectors.toList());
    }

    /**
     * Generates a stable cache key based on location rounded to 2 decimal places.
     * This groups nearby users into geographic clusters (~1.1km grid cells).
     */
    public String buildCacheKey(double lat, double lon) {
        double roundedLat = Math.round(lat * 100.0) / 100.0;
        double roundedLon = Math.round(lon * 100.0) / 100.0;
        return String.format("%.2f_%.2f", roundedLat, roundedLon);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private double distanceFromUser(Article article, double userLat, double userLon) {
        if (article.getLatitude() == null || article.getLongitude() == null) {
            return Double.MAX_VALUE;
        }
        return newsService.haversineDistance(
            userLat, userLon, article.getLatitude(), article.getLongitude()
        );
    }

    private double computeGeoProximityBoost(double distanceKm) {
        // Boost: articles close to user get up to 2x multiplier, far ones get ~1x
        return 1.0 + (1.0 / (1.0 + distanceKm));
    }

    private record ArticleWithScore(Article article, double score, double distanceKm) {}
}
