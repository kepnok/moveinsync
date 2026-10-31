package com.moveinsync.config.core.codec;

import com.moveinsync.config.core.model.ConfigValue;
import com.moveinsync.config.core.model.ScopeRef;
import java.util.Objects;

public record DiffEntry(
        String settingKey,
        ScopeRef scope,
        ConfigValue before,
        ConfigValue after
) {
    public DiffEntry {
        Objects.requireNonNull(settingKey, "settingKey cannot be null");
        Objects.requireNonNull(scope, "scope cannot be null");
    }
}

