package com.example.campushub.exceptions.auth;

public class RevokedTokenException extends RuntimeException {
    public RevokedTokenException(String message) {
        super(message);
    }

    public RevokedTokenException(String message, Throwable cause) {
        super(message, cause);
    }
    
}
