package com.moveinsync.config.server.repository;

import com.moveinsync.config.core.model.ChangeSet;
import com.moveinsync.config.core.model.SettingDefinition;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ChangeSetRepository {
    void insert(ChangeSet cs, String idempotencyKey);
    Optional<ChangeSet> findByVersion(long version, Map<String, SettingDefinition> definitions);
    Optional<ChangeSet> findByIdempotencyKey(String idempotencyKey, Map<String, SettingDefinition> definitions);
    List<ChangeSet> findRange(long fromVersion, long toVersion, Map<String, SettingDefinition> definitions);
    List<ChangeSet> findRecent(int limit, Map<String, SettingDefinition> definitions);
    List<ChangeSet> history(String tenantId, Long fromVersion, int limit, Map<String, SettingDefinition> definitions);
}

