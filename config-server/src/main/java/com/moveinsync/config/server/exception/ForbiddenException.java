package com.moveinsync.config.server.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ConfigException {
    public ForbiddenException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.FORBIDDEN;
    }

    @Override
    public String code() {
        return "FORBIDDEN";
    }
}

