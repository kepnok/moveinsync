package com.moveinsync.config.core.model;

public sealed interface ChangeItem permits OverrideChange, DefinitionChange {
    String settingKey();
}

