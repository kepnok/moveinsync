package com.moveinsync.config.core.exception;

import com.moveinsync.config.core.validation.Violation;
import java.util.Collections;
import java.util.List;

public final class ValidationFailedException extends RuntimeException {
    private final List<Violation> violations;

    public ValidationFailedException(List<Violation> violations) {
        super("Validation failed: " + violations);
        this.violations = violations != null ? List.copyOf(violations) : Collections.emptyList();
    }

    public ValidationFailedException(String code, String settingKey, String message) {
        this(List.of(new Violation(code, settingKey, message)));
    }

    public List<Violation> violations() {
        return violations;
    }
}

