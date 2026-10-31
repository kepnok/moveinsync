package com.moveinsync.config.server.exception;

import org.springframework.http.HttpStatus;

public class VersionConflictException extends ConfigException {
    public VersionConflictException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }

    @Override
    public String code() {
        return "VERSION_CONFLICT";
    }
}

