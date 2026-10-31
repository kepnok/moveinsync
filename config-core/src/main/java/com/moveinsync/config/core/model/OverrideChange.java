package com.moveinsync.config.core.model;

import java.util.Objects;

public record OverrideChange(
        String settingKey,
        ScopeRef scope,
        ConfigValue oldValue,
        ConfigValue newValue
) implements ChangeItem {
    public OverrideChange {
        Objects.requireNonNull(settingKey, "settingKey cannot be null");
        Objects.requireNonNull(scope, "scope cannot be null");
    }

    public boolean isDelete() {
        return newValue == null;
    }
}
