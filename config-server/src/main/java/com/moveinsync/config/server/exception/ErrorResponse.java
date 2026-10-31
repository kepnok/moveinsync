package com.moveinsync.config.server.exception;

import com.moveinsync.config.core.validation.Violation;
import java.util.Collections;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        String correlationId,
        List<Violation> violations
) {
    public ErrorResponse {
        violations = violations != null ? List.copyOf(violations) : Collections.emptyList();
    }
}

