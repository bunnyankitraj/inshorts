package com.inshorts.news.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private String error;
    private String message;
    private int status;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    private String path;
}
