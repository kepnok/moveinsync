package com.moveinsync.config.sdk;

import com.moveinsync.config.core.model.ConfigSnapshot;

@FunctionalInterface
public interface ConfigChangeListener {
    void onChange(ConfigSnapshot snapshot);
}
