package com.moveinsync.config.sdk;

public class ConfigUnavailableException extends RuntimeException {
    public ConfigUnavailableException(String message) {
        super(message);
    }

    public ConfigUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

