package com.example.campushub.exceptions;

public class RecommendationClientException extends RuntimeException {
    public RecommendationClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public RecommendationClientException(String message) {
        super(message);
    }
}
