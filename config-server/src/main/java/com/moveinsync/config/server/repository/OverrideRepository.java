package com.moveinsync.config.server.repository;

import com.moveinsync.config.core.model.ConfigValue;
import com.moveinsync.config.core.model.ScopeRef;
import com.moveinsync.config.core.model.SettingDefinition;
import java.util.List;
import java.util.Map;

public interface OverrideRepository {
    record OverrideRow(String settingKey, ScopeRef scope, ConfigValue value, long version) {}

    List<OverrideRow> findAll(Map<String, SettingDefinition> definitions);
    void upsert(String settingKey, ScopeRef scope, ConfigValue value, long version);
    void delete(String settingKey, ScopeRef scope);
}

