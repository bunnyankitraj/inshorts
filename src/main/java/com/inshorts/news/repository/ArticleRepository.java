package com.inshorts.news.repository;

import com.inshorts.news.model.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArticleRepository extends JpaRepository<Article, String> {

    // ---- category endpoint ----
    @Query("SELECT a FROM Article a WHERE LOWER(a.categoryRaw) LIKE LOWER(CONCAT('%', :category, '%')) ORDER BY a.publicationDate DESC")
    List<Article> findByCategory(@Param("category") String category);

    // ---- source endpoint ----
    @Query("SELECT a FROM Article a WHERE LOWER(a.sourceName) = LOWER(:source) ORDER BY a.publicationDate DESC")
    List<Article> findBySource(@Param("source") String source);

    // ---- score endpoint ----
    @Query("SELECT a FROM Article a WHERE a.relevanceScore >= :threshold ORDER BY a.relevanceScore DESC")
    List<Article> findByScoreAboveThreshold(@Param("threshold") double threshold);

    // ---- search endpoint ----
    @Query("SELECT a FROM Article a WHERE " +
           "LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.description) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "ORDER BY a.relevanceScore DESC")
    List<Article> findByTextSearch(@Param("query") String query);

    // ---- nearby endpoint — fetch all with coords, distance computed in service ----
    @Query("SELECT a FROM Article a WHERE a.latitude IS NOT NULL AND a.longitude IS NOT NULL")
    List<Article> findAllWithCoordinates();
}
