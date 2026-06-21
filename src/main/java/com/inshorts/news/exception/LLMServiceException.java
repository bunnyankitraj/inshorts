package com.inshorts.news.exception;

public class LLMServiceException extends RuntimeException {
    public LLMServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
