package com.moveinsync.config.server.exception;

import org.springframework.http.HttpStatus;

public class ResyncRequiredException extends ConfigException {
    public ResyncRequiredException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.GONE;
    }

    @Override
    public String code() {
        return "RESYNC_REQUIRED";
    }
}

