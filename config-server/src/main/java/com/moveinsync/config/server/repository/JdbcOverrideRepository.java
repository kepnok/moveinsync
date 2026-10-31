package com.moveinsync.config.server.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.config.core.model.ConfigValue;
import com.moveinsync.config.core.model.ScopeRef;
import com.moveinsync.config.core.model.ScopeType;
import com.moveinsync.config.core.model.SettingDefinition;
import com.moveinsync.config.core.strategy.ValueTypeRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcOverrideRepository implements OverrideRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper mapper;
    private final ValueTypeRegistry typeRegistry;

    public JdbcOverrideRepository(JdbcTemplate jdbcTemplate, ObjectMapper mapper, ValueTypeRegistry typeRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.mapper = mapper;
        this.typeRegistry = typeRegistry;
    }

    @Override
    public List<OverrideRow> findAll(Map<String, SettingDefinition> definitions) {
        return jdbcTemplate.query(
                "SELECT setting_key, scope_type, scope_id, value::text, updated_version FROM overrides",
                (rs, rowNum) -> {
                    String settingKey = rs.getString("setting_key");
                    ScopeType scopeType = ScopeType.valueOf(rs.getString("scope_type"));
                    String scopeId = rs.getString("scope_id");
                    String valueJson = rs.getString("value");
                    long version = rs.getLong("updated_version");

                    SettingDefinition def = definitions.get(settingKey);
                    if (def == null) {
                        return null;
                    }

                    try {
                        JsonNode valNode = mapper.readTree(valueJson);
                        ConfigValue value = typeRegistry.parse(def.type(), valNode, def.rules(), settingKey);
                        ScopeRef scope = new ScopeRef(scopeType, scopeId);
                        return new OverrideRow(settingKey, scope, value, version);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to deserialize override for " + settingKey, e);
                    }
                }
        ).stream().filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public void upsert(String settingKey, ScopeRef scope, ConfigValue value, long version) {
        String json = value.toJson().toString();
        String tenantId = scope.tenantId();

        jdbcTemplate.update("""
            INSERT INTO overrides (setting_key, scope_type, scope_id, tenant_id, value, updated_version)
            VALUES (?, ?, ?, ?, ?::jsonb, ?)
            ON CONFLICT (setting_key, scope_type, scope_id) DO UPDATE
            SET value = EXCLUDED.value,
                updated_version = EXCLUDED.updated_version,
                tenant_id = EXCLUDED.tenant_id
            """,
                settingKey,
                scope.type().name(),
                scope.id(),
                tenantId,
                json,
                version
        );
    }

    @Override
    public void delete(String settingKey, ScopeRef scope) {
        jdbcTemplate.update(
                "DELETE FROM overrides WHERE setting_key = ? AND scope_type = ? AND scope_id = ?",
                settingKey, scope.type().name(), scope.id()
        );
    }
}

