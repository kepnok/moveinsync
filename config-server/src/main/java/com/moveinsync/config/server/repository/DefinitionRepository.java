package com.moveinsync.config.server.repository;

import com.moveinsync.config.core.model.SettingDefinition;
import java.util.List;
import java.util.Optional;

public interface DefinitionRepository {
    List<SettingDefinition> findAll();
    Optional<SettingDefinition> findByKey(String key);
    void upsert(SettingDefinition def, long version);
}

