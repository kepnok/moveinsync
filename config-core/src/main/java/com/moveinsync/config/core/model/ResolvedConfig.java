package com.moveinsync.config.core.model;

import java.util.Objects;

public record ResolvedConfig(
        String key,
        ConfigValue value,
        ScopeRef source,
        boolean fromDefault,
        long snapshotVersion
) {
    public ResolvedConfig {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
    }
}
