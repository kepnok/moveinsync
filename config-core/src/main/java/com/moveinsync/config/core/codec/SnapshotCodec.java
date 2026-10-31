package com.moveinsync.config.core.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.moveinsync.config.core.model.*;
import com.moveinsync.config.core.strategy.ValueTypeRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

public final class SnapshotCodec {
    private final ObjectMapper mapper;
    private final ValueTypeRegistry typeRegistry;

    public SnapshotCodec() {
        this(new ValueTypeRegistry());
    }

    public SnapshotCodec(ValueTypeRegistry typeRegistry) {
        this.typeRegistry = Objects.requireNonNull(typeRegistry, "typeRegistry cannot be null");
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public byte[] encode(ConfigSnapshot s) {
        try {
            return mapper.writeValueAsBytes(encodeToJson(s));
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode ConfigSnapshot", e);
        }
    }

    public ObjectNode encodeToJson(ConfigSnapshot s) {
        ObjectNode root = mapper.createObjectNode();
        root.put("epoch", s.epoch().toString());
        root.put("version", s.version());

        ObjectNode defsNode = root.putObject("definitions");
        for (Map.Entry<String, SettingDefinition> entry : s.definitions().entrySet()) {
            SettingDefinition def = entry.getValue();
            ObjectNode defNode = defsNode.putObject(entry.getKey());
            defNode.put("key", def.key());
            defNode.put("type", def.type().name());
            defNode.set("defaultValue", def.defaultValue().toJson());
            defNode.set("rules", def.rules().raw());
            defNode.put("description", def.description());
        }

        ObjectNode overridesNode = root.putObject("overrides");
        for (Map.Entry<String, Map<ScopeRef, ConfigValue>> entry : s.overrides().entrySet()) {
            ObjectNode scopeMapNode = overridesNode.putObject(entry.getKey());
            for (Map.Entry<ScopeRef, ConfigValue> scopeEntry : entry.getValue().entrySet()) {
                scopeMapNode.set(scopeEntry.getKey().key(), scopeEntry.getValue().toJson());
            }
        }

        return root;
    }

    public ConfigSnapshot decode(byte[] json) {
        try {
            JsonNode root = mapper.readTree(json);
            return decode(root);
        } catch (IOException e) {
            throw new RuntimeException("Failed to decode ConfigSnapshot from bytes", e);
        }
    }

    public ConfigSnapshot decode(JsonNode root) {
        UUID epoch = UUID.fromString(root.get("epoch").asText());
        long version = root.get("version").asLong();

        Map<String, SettingDefinition> definitions = new HashMap<>();
        JsonNode defsNode = root.get("definitions");
        if (defsNode != null && defsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = defsNode.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                JsonNode dNode = entry.getValue();
                String key = dNode.get("key").asText();
                ValueType type = ValueType.valueOf(dNode.get("type").asText());
                ValidationRules rules = new ValidationRules(dNode.get("rules"));
                ConfigValue defaultValue = typeRegistry.parse(type, dNode.get("defaultValue"), rules, key);
                String desc = dNode.hasNonNull("description") ? dNode.get("description").asText() : "";
                definitions.put(key, new SettingDefinition(key, type, defaultValue, rules, desc));
            }
        }

        Map<String, Map<ScopeRef, ConfigValue>> overrides = new HashMap<>();
        JsonNode overridesNode = root.get("overrides");
        if (overridesNode != null && overridesNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = overridesNode.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                String key = entry.getKey();
                SettingDefinition def = definitions.get(key);
                if (def == null) {
                    continue;
                }

                Map<ScopeRef, ConfigValue> scopeMap = new HashMap<>();
                Iterator<Map.Entry<String, JsonNode>> scopeIt = entry.getValue().fields();
                while (scopeIt.hasNext()) {
                    Map.Entry<String, JsonNode> scopeEntry = scopeIt.next();
                    ScopeRef scope = ScopeRef.parse(scopeEntry.getKey());
                    ConfigValue value = typeRegistry.parse(def.type(), scopeEntry.getValue(), def.rules(), key);
                    scopeMap.put(scope, value);
                }
                overrides.put(key, scopeMap);
            }
        }

        return new ConfigSnapshot(epoch, version, definitions, overrides);
    }

    public JsonNode encode(ChangeSet cs) {
        ObjectNode node = mapper.createObjectNode();
        node.put("version", cs.version());
        node.put("id", cs.id().toString());
        node.put("kind", cs.kind().name());
        node.put("authorId", cs.authorId());
        node.put("reason", cs.reason());
        node.put("createdAt", cs.createdAt().toString());
        if (cs.rolloutId() != null) {
            node.put("rolloutId", cs.rolloutId().toString());
        }
        if (cs.rollbackTo() != null) {
            node.put("rollbackTo", cs.rollbackTo());
        }

        ArrayNode itemsArr = node.putArray("items");
        for (ChangeItem item : cs.items()) {
            ObjectNode itemNode = itemsArr.addObject();
            if (item instanceof DefinitionChange dc) {
                itemNode.put("itemType", "DEFINITION");
                itemNode.put("settingKey", dc.settingKey());
                if (dc.oldDef() != null) {
                    ObjectNode oldDefNode = itemNode.putObject("oldDef");
                    oldDefNode.put("key", dc.oldDef().key());
                    oldDefNode.put("type", dc.oldDef().type().name());
                    oldDefNode.set("defaultValue", dc.oldDef().defaultValue().toJson());
                    oldDefNode.set("rules", dc.oldDef().rules().raw());
                    oldDefNode.put("description", dc.oldDef().description());
                }
                ObjectNode newDefNode = itemNode.putObject("newDef");
                newDefNode.put("key", dc.newDef().key());
                newDefNode.put("type", dc.newDef().type().name());
                newDefNode.set("defaultValue", dc.newDef().defaultValue().toJson());
                newDefNode.set("rules", dc.newDef().rules().raw());
                newDefNode.put("description", dc.newDef().description());
            } else if (item instanceof OverrideChange oc) {
                itemNode.put("itemType", "OVERRIDE");
                itemNode.put("settingKey", oc.settingKey());
                itemNode.put("scope", oc.scope().key());
                if (oc.newValue() != null) {
                    itemNode.put("valueType", oc.newValue().type().name());
                } else if (oc.oldValue() != null) {
                    itemNode.put("valueType", oc.oldValue().type().name());
                }
                itemNode.set("oldValue", oc.oldValue() != null ? oc.oldValue().toJson() : NullNode.getInstance());
                itemNode.set("newValue", oc.newValue() != null ? oc.newValue().toJson() : NullNode.getInstance());
            }
        }

        return node;
    }

    public ChangeSet decodeChangeSet(JsonNode n, Map<String, SettingDefinition> knownDefs) {
        long version = n.get("version").asLong();
        UUID id = UUID.fromString(n.get("id").asText());
        ChangeKind kind = ChangeKind.valueOf(n.get("kind").asText());
        String authorId = n.get("authorId").asText();
        String reason = n.get("reason").asText();
        Instant createdAt = n.hasNonNull("createdAt") ? Instant.parse(n.get("createdAt").asText()) : Instant.now();
        UUID rolloutId = n.hasNonNull("rolloutId") ? UUID.fromString(n.get("rolloutId").asText()) : null;
        Long rollbackTo = n.hasNonNull("rollbackTo") ? n.get("rollbackTo").asLong() : null;

        Map<String, SettingDefinition> defsMap = new HashMap<>(knownDefs != null ? knownDefs : Collections.emptyMap());
        List<ChangeItem> items = new ArrayList<>();
        JsonNode itemsArr = n.get("items");
        if (itemsArr != null && itemsArr.isArray()) {
            for (JsonNode itemNode : itemsArr) {
                String itemType = itemNode.hasNonNull("itemType") ? itemNode.get("itemType").asText() : "";
                String settingKey = itemNode.hasNonNull("settingKey") ? itemNode.get("settingKey").asText() : "";
                if ("DEFINITION".equalsIgnoreCase(itemType) || itemNode.has("newDef")) {
                    SettingDefinition oldDef = decodeDefNode(itemNode.get("oldDef"));
                    SettingDefinition newDef = decodeDefNode(itemNode.get("newDef"));
                    defsMap.put(settingKey, newDef);
                    items.add(new DefinitionChange(settingKey, oldDef, newDef));
                } else if ("OVERRIDE".equalsIgnoreCase(itemType) || itemNode.has("scope")) {
                    ScopeRef scope;
                    JsonNode scopeNode = itemNode.get("scope");
                    if (scopeNode.isTextual()) {
                        scope = ScopeRef.parse(scopeNode.asText());
                    } else {
                        ScopeType st = ScopeType.valueOf(scopeNode.get("type").asText().toUpperCase());
                        String sid = scopeNode.hasNonNull("id") ? scopeNode.get("id").asText() : null;
                        scope = new ScopeRef(st, sid);
                    }

                    SettingDefinition def = defsMap.get(settingKey);
                    ValueType vt = def != null ? def.type() :
                            (itemNode.hasNonNull("valueType") ? ValueType.valueOf(itemNode.get("valueType").asText()) : null);
                    ValidationRules rules = def != null ? def.rules() : new ValidationRules(mapper.createObjectNode());

                    ConfigValue oldVal = null;
                    ConfigValue newVal = null;
                    if (vt != null) {
                        if (itemNode.hasNonNull("oldValue") && !itemNode.get("oldValue").isNull()) {
                            oldVal = typeRegistry.parse(vt, itemNode.get("oldValue"), rules, settingKey);
                        }
                        if (itemNode.hasNonNull("newValue") && !itemNode.get("newValue").isNull()) {
                            newVal = typeRegistry.parse(vt, itemNode.get("newValue"), rules, settingKey);
                        }
                    }
                    items.add(new OverrideChange(settingKey, scope, oldVal, newVal));
                }
            }
        }

        return new ChangeSet(version, id, kind, authorId, reason, createdAt, items, rolloutId, rollbackTo);
    }

    public ChangeSet decodeChangeSet(JsonNode n) {
        return decodeChangeSet(n, Collections.emptyMap());
    }

    private SettingDefinition decodeDefNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String key = node.get("key").asText();
        ValueType type = ValueType.valueOf(node.get("type").asText());
        ValidationRules rules = new ValidationRules(node.get("rules"));
        ConfigValue defValue = typeRegistry.parse(type, node.get("defaultValue"), rules, key);
        String desc = node.hasNonNull("description") ? node.get("description").asText() : "";
        return new SettingDefinition(key, type, defValue, rules, desc);
    }
}

