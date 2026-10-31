package com.moveinsync.config.core.validation;

public record Violation(String code, String settingKey, String message) {
    public Violation {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Violation code cannot be empty");
        }
        if (settingKey == null || settingKey.isBlank()) {
            throw new IllegalArgumentException("Violation settingKey cannot be empty");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Violation message cannot be empty");
        }
    }
}
