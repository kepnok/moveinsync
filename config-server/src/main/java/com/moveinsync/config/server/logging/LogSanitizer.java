package com.moveinsync.config.server.logging;

import java.util.regex.Pattern;

public final class LogSanitizer {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?:\\+?\\d{1,3}[- ]?)?\\(?\\d{3}\\)?[- ]?\\d{3}[- ]?\\d{4}");

    private LogSanitizer() {}

    public static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        String sanitized = EMAIL_PATTERN.matcher(input).replaceAll("***@***.***");
        return PHONE_PATTERN.matcher(sanitized).replaceAll("***-***-****");
    }
}

