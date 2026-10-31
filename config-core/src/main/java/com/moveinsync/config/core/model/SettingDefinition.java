package com.moveinsync.config.core.model;

import java.util.Objects;

public record SettingDefinition(
        String key,
        ValueType type,
        ConfigValue defaultValue,
        ValidationRules rules,
        String description
) {
    public SettingDefinition {
        Objects.requireNonNull(key, "Setting key cannot be null");
        Objects.requireNonNull(type, "ValueType cannot be null");
        Objects.requireNonNull(defaultValue, "Default value cannot be null");
        if (rules == null) {
            rules = ValidationRules.empty();
        }
        if (defaultValue.type() != type) {
            throw new IllegalArgumentException("Default value type " + defaultValue.type() + " does not match definition type " + type);
        }
    }
}

