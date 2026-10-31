package com.moveinsync.config.core.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record ExplainResult(
        ResolvedConfig effective,
        List<LevelEntry> levels
) {
    public ExplainResult {
        Objects.requireNonNull(effective, "effective cannot be null");
        levels = levels != null ? List.copyOf(levels) : Collections.emptyList();
    }
}

