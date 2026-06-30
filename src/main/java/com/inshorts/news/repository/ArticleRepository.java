package com.inshorts.news.repository;

import com.inshorts.news.model.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArticleRepository extends JpaRepository<Article, String> {

    // ---- category endpoint ---- (paged in SQL; Page carries total count for metadata)
    @Query("SELECT a FROM Article a WHERE LOWER(a.categoryRaw) LIKE LOWER(CONCAT('%', :category, '%')) ORDER BY a.publicationDate DESC")
    Page<Article> findByCategory(@Param("category") String category, Pageable pageable);

    // ---- source endpoint ----
    @Query("SELECT a FROM Article a WHERE LOWER(a.sourceName) = LOWER(:source) ORDER BY a.publicationDate DESC")
    Page<Article> findBySource(@Param("source") String source, Pageable pageable);

    // ---- score endpoint ----
    @Query("SELECT a FROM Article a WHERE a.relevanceScore >= :threshold ORDER BY a.relevanceScore DESC")
    Page<Article> findByScoreAboveThreshold(@Param("threshold") double threshold, Pageable pageable);

    // ---- search endpoint ---- (candidate set capped via Pageable, re-ranked in service)
    @Query("SELECT a FROM Article a WHERE " +
           "LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.description) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "ORDER BY a.relevanceScore DESC")
    List<Article> findByTextSearch(@Param("query") String query, Pageable pageable);

    // ---- nearby endpoint — bounding box pre-filter, exact Haversine applied in service ----
    @Query("SELECT a FROM Article a WHERE " +
           "a.latitude IS NOT NULL AND a.longitude IS NOT NULL AND " +
           "a.latitude BETWEEN :latMin AND :latMax AND " +
           "a.longitude BETWEEN :lonMin AND :lonMax")
    List<Article> findWithinBoundingBox(
        @Param("latMin") double latMin,
        @Param("latMax") double latMax,
        @Param("lonMin") double lonMin,
        @Param("lonMax") double lonMax);
}
