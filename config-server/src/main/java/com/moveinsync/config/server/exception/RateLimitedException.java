package com.moveinsync.config.server.exception;

import org.springframework.http.HttpStatus;

public class RateLimitedException extends ConfigException {
    public RateLimitedException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }

    @Override
    public String code() {
        return "RATE_LIMITED";
    }
}

