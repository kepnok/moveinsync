package com.moveinsync.config.server.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ConfigException {
    public NotFoundException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }

    @Override
    public String code() {
        return "NOT_FOUND";
    }
}

