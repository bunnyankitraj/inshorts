package com.inshorts.news.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsApiResponse {

    private Metadata metadata;
    private List<ArticleResponse> articles;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {
        private int totalResults;
        private int page;
        private String queryUsed;
        private List<String> intent;
        private List<String> entities;
        private String endpoint;
    }
}
