package com.moveinsync.config.server.exception;

import org.springframework.http.HttpStatus;

public abstract class ConfigException extends RuntimeException {
    public ConfigException(String message) {
        super(message);
    }

    public abstract HttpStatus status();
    public abstract String code();
}

