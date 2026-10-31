package com.moveinsync.config.server.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.config.core.model.ConfigValue;
import com.moveinsync.config.core.model.SettingDefinition;
import com.moveinsync.config.core.model.ValidationRules;
import com.moveinsync.config.core.model.ValueType;
import com.moveinsync.config.core.strategy.ValueTypeRegistry;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcDefinitionRepository implements DefinitionRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper mapper;
    private final ValueTypeRegistry typeRegistry;

    public JdbcDefinitionRepository(JdbcTemplate jdbcTemplate, ObjectMapper mapper, ValueTypeRegistry typeRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.mapper = mapper;
        this.typeRegistry = typeRegistry;
    }

    private RowMapper<SettingDefinition> rowMapper() {
        return (rs, rowNum) -> {
            String key = rs.getString("setting_key");
            ValueType type = ValueType.valueOf(rs.getString("value_type"));
            String defaultJson = rs.getString("default_value");
            String rulesJson = rs.getString("rules");
            String desc = rs.getString("description");

            try {
                JsonNode rulesNode = rulesJson != null ? mapper.readTree(rulesJson) : mapper.createObjectNode();
                ValidationRules rules = new ValidationRules(rulesNode);

                JsonNode defaultNode = mapper.readTree(defaultJson);
                ConfigValue defaultValue = typeRegistry.parse(type, defaultNode, rules, key);

                return new SettingDefinition(key, type, defaultValue, rules, desc != null ? desc : "");
            } catch (IOException e) {
                throw new RuntimeException("Failed to deserialize setting definition: " + key, e);
            }
        };
    }

    @Override
    public List<SettingDefinition> findAll() {
        return jdbcTemplate.query("SELECT setting_key, value_type, default_value::text, rules::text, description FROM setting_definitions ORDER BY setting_key", rowMapper());
    }

    @Override
    public Optional<SettingDefinition> findByKey(String key) {
        try {
            SettingDefinition def = jdbcTemplate.queryForObject(
                    "SELECT setting_key, value_type, default_value::text, rules::text, description FROM setting_definitions WHERE setting_key = ?",
                    rowMapper(), key);
            return Optional.ofNullable(def);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void upsert(SettingDefinition def, long version) {
        String defaultJson = def.defaultValue().toJson().toString();
        String rulesJson = def.rules().raw().toString();

        jdbcTemplate.update("""
            INSERT INTO setting_definitions (setting_key, value_type, default_value, rules, description, updated_version)
            VALUES (?, ?, ?::jsonb, ?::jsonb, ?, ?)
            ON CONFLICT (setting_key) DO UPDATE
            SET value_type = EXCLUDED.value_type,
                default_value = EXCLUDED.default_value,
                rules = EXCLUDED.rules,
                description = EXCLUDED.description,
                updated_version = EXCLUDED.updated_version
            """,
                def.key(),
                def.type().name(),
                defaultJson,
                rulesJson,
                def.description(),
                version
        );
    }
}

