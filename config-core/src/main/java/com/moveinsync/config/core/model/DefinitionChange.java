package com.moveinsync.config.core.model;

import java.util.Objects;

public record DefinitionChange(
        String settingKey,
        SettingDefinition oldDef,
        SettingDefinition newDef
) implements ChangeItem {
    public DefinitionChange {
        Objects.requireNonNull(settingKey, "settingKey cannot be null");
        Objects.requireNonNull(newDef, "newDef cannot be null");
    }
}

