package com.moveinsync.config.core.model;

import java.util.Objects;

public record LevelEntry(
        ScopeRef scope,
        ConfigValue valueOrNull,
        boolean applied
) {
    public LevelEntry {
        Objects.requireNonNull(scope, "scope cannot be null");
    }
}

