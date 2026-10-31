package com.moveinsync.config.server.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ConfigException {
    public BadRequestException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }

    @Override
    public String code() {
        return "BAD_REQUEST";
    }
}
