package com.moveinsync.config.server.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.config.core.model.*;
import com.moveinsync.config.core.strategy.ValueTypeRegistry;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class JdbcChangeSetRepository implements ChangeSetRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper mapper;
    private final ValueTypeRegistry typeRegistry;

    public JdbcChangeSetRepository(JdbcTemplate jdbcTemplate, ObjectMapper mapper, ValueTypeRegistry typeRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.mapper = mapper;
        this.typeRegistry = typeRegistry;
    }

    @Override
    public void insert(ChangeSet cs, String idempotencyKey) {
        jdbcTemplate.update("""
            INSERT INTO change_sets (version, change_set_id, kind, author_id, reason, created_at, idempotency_key, rollout_id, rollback_to)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                cs.version(),
                cs.id(),
                cs.kind().name(),
                cs.authorId(),
                cs.reason(),
                Timestamp.from(cs.createdAt()),
                idempotencyKey,
                cs.rolloutId(),
                cs.rollbackTo()
        );

        for (ChangeItem item : cs.items()) {
            if (item instanceof DefinitionChange dc) {
                String oldJson = dc.oldDef() != null ? defToJson(dc.oldDef()) : null;
                String newJson = defToJson(dc.newDef());

                jdbcTemplate.update("""
                    INSERT INTO change_items (version, item_type, setting_key, scope_type, scope_id, tenant_id, old_value, new_value)
                    VALUES (?, 'DEFINITION', ?, null, null, null, ?::jsonb, ?::jsonb)
                    """,
                        cs.version(),
                        dc.settingKey(),
                        oldJson,
                        newJson
                );
            } else if (item instanceof OverrideChange oc) {
                String oldJson = oc.oldValue() != null ? oc.oldValue().toJson().toString() : null;
                String newJson = oc.newValue() != null ? oc.newValue().toJson().toString() : null;

                jdbcTemplate.update("""
                    INSERT INTO change_items (version, item_type, setting_key, scope_type, scope_id, tenant_id, old_value, new_value)
                    VALUES (?, 'OVERRIDE', ?, ?, ?, ?, ?::jsonb, ?::jsonb)
                    """,
                        cs.version(),
                        oc.settingKey(),
                        oc.scope().type().name(),
                        oc.scope().id(),
                        oc.scope().tenantId(),
                        oldJson,
                        newJson
                );
            }
        }
    }

    private String defToJson(SettingDefinition def) {
        var node = mapper.createObjectNode();
        node.put("key", def.key());
        node.put("type", def.type().name());
        node.set("defaultValue", def.defaultValue().toJson());
        node.set("rules", def.rules().raw());
        node.put("description", def.description());
        return node.toString();
    }

    @Override
    public Optional<ChangeSet> findByVersion(long version, Map<String, SettingDefinition> definitions) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT version, change_set_id, kind, author_id, reason, created_at, rollout_id, rollback_to FROM change_sets WHERE version = ?",
                    (rs, rowNum) -> {
                        long v = rs.getLong("version");
                        UUID id = (UUID) rs.getObject("change_set_id");
                        ChangeKind kind = ChangeKind.valueOf(rs.getString("kind"));
                        String author = rs.getString("author_id");
                        String reason = rs.getString("reason");
                        Instant created = rs.getTimestamp("created_at").toInstant();
                        UUID rolloutId = (UUID) rs.getObject("rollout_id");
                        Long rollbackTo = rs.getObject("rollback_to") != null ? rs.getLong("rollback_to") : null;

                        List<ChangeItem> items = loadItems(v, definitions);
                        return new ChangeSet(v, id, kind, author, reason, created, items, rolloutId, rollbackTo);
                    },
                    version
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ChangeSet> findByIdempotencyKey(String idempotencyKey, Map<String, SettingDefinition> definitions) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT version, change_set_id, kind, author_id, reason, created_at, rollout_id, rollback_to FROM change_sets WHERE idempotency_key = ?",
                    (rs, rowNum) -> {
                        long v = rs.getLong("version");
                        UUID id = (UUID) rs.getObject("change_set_id");
                        ChangeKind kind = ChangeKind.valueOf(rs.getString("kind"));
                        String author = rs.getString("author_id");
                        String reason = rs.getString("reason");
                        Instant created = rs.getTimestamp("created_at").toInstant();
                        UUID rolloutId = (UUID) rs.getObject("rollout_id");
                        Long rollbackTo = rs.getObject("rollback_to") != null ? rs.getLong("rollback_to") : null;

                        List<ChangeItem> items = loadItems(v, definitions);
                        return new ChangeSet(v, id, kind, author, reason, created, items, rolloutId, rollbackTo);
                    },
                    idempotencyKey
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<ChangeSet> findRange(long fromVersion, long toVersion, Map<String, SettingDefinition> definitions) {
        return jdbcTemplate.query(
                "SELECT version, change_set_id, kind, author_id, reason, created_at, rollout_id, rollback_to FROM change_sets WHERE version BETWEEN ? AND ? ORDER BY version ASC",
                (rs, rowNum) -> {
                    long v = rs.getLong("version");
                    UUID id = (UUID) rs.getObject("change_set_id");
                    ChangeKind kind = ChangeKind.valueOf(rs.getString("kind"));
                    String author = rs.getString("author_id");
                    String reason = rs.getString("reason");
                    Instant created = rs.getTimestamp("created_at").toInstant();
                    UUID rolloutId = (UUID) rs.getObject("rollout_id");
                    Long rollbackTo = rs.getObject("rollback_to") != null ? rs.getLong("rollback_to") : null;

                    List<ChangeItem> items = loadItems(v, definitions);
                    return new ChangeSet(v, id, kind, author, reason, created, items, rolloutId, rollbackTo);
                },
                fromVersion, toVersion
        );
    }

    @Override
    public List<ChangeSet> findRecent(int limit, Map<String, SettingDefinition> definitions) {
        List<ChangeSet> list = jdbcTemplate.query(
                "SELECT version, change_set_id, kind, author_id, reason, created_at, rollout_id, rollback_to FROM change_sets ORDER BY version DESC LIMIT ?",
                (rs, rowNum) -> {
                    long v = rs.getLong("version");
                    UUID id = (UUID) rs.getObject("change_set_id");
                    ChangeKind kind = ChangeKind.valueOf(rs.getString("kind"));
                    String author = rs.getString("author_id");
                    String reason = rs.getString("reason");
                    Instant created = rs.getTimestamp("created_at").toInstant();
                    UUID rolloutId = (UUID) rs.getObject("rollout_id");
                    Long rollbackTo = rs.getObject("rollback_to") != null ? rs.getLong("rollback_to") : null;

                    List<ChangeItem> items = loadItems(v, definitions);
                    return new ChangeSet(v, id, kind, author, reason, created, items, rolloutId, rollbackTo);
                },
                limit
        );
        Collections.reverse(list); // return in ascending order
        return list;
    }

    @Override
    public List<ChangeSet> history(String tenantId, Long fromVersion, int limit, Map<String, SettingDefinition> definitions) {
        String sql;
        List<Object> params = new ArrayList<>();
        if (tenantId == null || tenantId.isBlank() || "*".equals(tenantId)) {
            if (fromVersion != null) {
                sql = "SELECT version, change_set_id, kind, author_id, reason, created_at, rollout_id, rollback_to FROM change_sets WHERE version <= ? ORDER BY version DESC LIMIT ?";
                params.add(fromVersion);
                params.add(limit);
            } else {
                sql = "SELECT version, change_set_id, kind, author_id, reason, created_at, rollout_id, rollback_to FROM change_sets ORDER BY version DESC LIMIT ?";
                params.add(limit);
            }
        } else {
            if (fromVersion != null) {
                sql = """
                    SELECT DISTINCT cs.version, cs.change_set_id, cs.kind, cs.author_id, cs.reason, cs.created_at, cs.rollout_id, cs.rollback_to
                    FROM change_sets cs
                    JOIN change_items ci ON cs.version = ci.version
                    WHERE (ci.tenant_id = ? OR ci.scope_type = 'GLOBAL' OR ci.item_type = 'DEFINITION')
                      AND cs.version <= ?
                    ORDER BY cs.version DESC LIMIT ?
                    """;
                params.add(tenantId);
                params.add(fromVersion);
                params.add(limit);
            } else {
                sql = """
                    SELECT DISTINCT cs.version, cs.change_set_id, cs.kind, cs.author_id, cs.reason, cs.created_at, cs.rollout_id, cs.rollback_to
                    FROM change_sets cs
                    JOIN change_items ci ON cs.version = ci.version
                    WHERE (ci.tenant_id = ? OR ci.scope_type = 'GLOBAL' OR ci.item_type = 'DEFINITION')
                    ORDER BY cs.version DESC LIMIT ?
                    """;
                params.add(tenantId);
                params.add(limit);
            }
        }

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            long v = rs.getLong("version");
            UUID id = (UUID) rs.getObject("change_set_id");
            ChangeKind kind = ChangeKind.valueOf(rs.getString("kind"));
            String author = rs.getString("author_id");
            String reason = rs.getString("reason");
            Instant created = rs.getTimestamp("created_at").toInstant();
            UUID rolloutId = (UUID) rs.getObject("rollout_id");
            Long rollbackTo = rs.getObject("rollback_to") != null ? rs.getLong("rollback_to") : null;

            List<ChangeItem> items = loadItems(v, definitions);
            return new ChangeSet(v, id, kind, author, reason, created, items, rolloutId, rollbackTo);
        }, params.toArray());
    }

    private List<ChangeItem> loadItems(long version, Map<String, SettingDefinition> definitions) {
        return jdbcTemplate.query(
                "SELECT item_type, setting_key, scope_type, scope_id, old_value::text, new_value::text FROM change_items WHERE version = ? ORDER BY id ASC",
                (rs, rowNum) -> {
                    String itemType = rs.getString("item_type");
                    String key = rs.getString("setting_key");
                    String oldJson = rs.getString("old_value");
                    String newJson = rs.getString("new_value");

                    if ("DEFINITION".equalsIgnoreCase(itemType)) {
                        SettingDefinition oldDef = decodeDef(oldJson);
                        SettingDefinition newDef = decodeDef(newJson);
                        return new DefinitionChange(key, oldDef, newDef);
                    } else {
                        ScopeType st = ScopeType.valueOf(rs.getString("scope_type"));
                        String scopeId = rs.getString("scope_id");
                        ScopeRef scope = new ScopeRef(st, scopeId);

                        SettingDefinition def = definitions.get(key);
                        ConfigValue oldVal = null;
                        ConfigValue newVal = null;
                        if (def != null) {
                            try {
                                if (oldJson != null) oldVal = typeRegistry.parse(def.type(), mapper.readTree(oldJson), def.rules(), key);
                                if (newJson != null) newVal = typeRegistry.parse(def.type(), mapper.readTree(newJson), def.rules(), key);
                            } catch (IOException e) {
                                throw new RuntimeException("Error reading override json", e);
                            }
                        }
                        return new OverrideChange(key, scope, oldVal, newVal);
                    }
                },
                version
        );
    }

    private SettingDefinition decodeDef(String json) {
        if (json == null) return null;
        try {
            JsonNode n = mapper.readTree(json);
            String k = n.get("key").asText();
            ValueType t = ValueType.valueOf(n.get("type").asText());
            ValidationRules r = new ValidationRules(n.get("rules"));
            ConfigValue v = typeRegistry.parse(t, n.get("defaultValue"), r, k);
            String desc = n.hasNonNull("description") ? n.get("description").asText() : "";
            return new SettingDefinition(k, t, v, r, desc);
        } catch (IOException e) {
            throw new RuntimeException("Error decoding def json", e);
        }
    }
}

