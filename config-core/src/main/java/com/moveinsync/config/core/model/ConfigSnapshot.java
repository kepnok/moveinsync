package com.moveinsync.config.core.model;

import java.util.*;

public final class ConfigSnapshot {
    private final UUID epoch;
    private final long version;
    private final Map<String, SettingDefinition> definitions;
    private final Map<String, Map<ScopeRef, ConfigValue>> overrides;

    public ConfigSnapshot(
            UUID epoch,
            long version,
            Map<String, SettingDefinition> definitions,
            Map<String, Map<ScopeRef, ConfigValue>> overrides
    ) {
        this.epoch = Objects.requireNonNull(epoch, "epoch cannot be null");
        this.version = version;
        this.definitions = makeUnmodifiableDefs(definitions);
        this.overrides = makeUnmodifiableOverrides(overrides);
    }

    private static Map<String, SettingDefinition> makeUnmodifiableDefs(Map<String, SettingDefinition> src) {
        if (src == null || src.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new HashMap<>(src));
    }

    private static Map<String, Map<ScopeRef, ConfigValue>> makeUnmodifiableOverrides(Map<String, Map<ScopeRef, ConfigValue>> src) {
        if (src == null || src.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Map<ScopeRef, ConfigValue>> outer = new HashMap<>(src.size());
        for (Map.Entry<String, Map<ScopeRef, ConfigValue>> e : src.entrySet()) {
            outer.put(e.getKey(), Collections.unmodifiableMap(new HashMap<>(e.getValue())));
        }
        return Collections.unmodifiableMap(outer);
    }

    public static ConfigSnapshot empty(UUID epoch) {
        return new ConfigSnapshot(epoch, 0L, Collections.emptyMap(), Collections.emptyMap());
    }

    public UUID epoch() {
        return epoch;
    }

    public long version() {
        return version;
    }

    public Map<String, SettingDefinition> definitions() {
        return definitions;
    }

    public Map<String, Map<ScopeRef, ConfigValue>> overrides() {
        return overrides;
    }

    public Optional<ConfigValue> overrideAt(String key, ScopeRef scope) {
        Map<ScopeRef, ConfigValue> scopeMap = overrides.get(key);
        if (scopeMap == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(scopeMap.get(scope));
    }

    public ConfigSnapshot withChangeSet(ChangeSet cs) {
        Objects.requireNonNull(cs, "ChangeSet cannot be null");

        // Copy-on-write definitions
        Map<String, SettingDefinition> nextDefs = new HashMap<>(this.definitions);

        // Copy-on-write overrides: outer map copy, inner map only copied on write
        Map<String, Map<ScopeRef, ConfigValue>> nextOverrides = new HashMap<>(this.overrides);

        for (ChangeItem item : cs.items()) {
            if (item instanceof DefinitionChange dc) {
                nextDefs.put(dc.settingKey(), dc.newDef());
            } else if (item instanceof OverrideChange oc) {
                String key = oc.settingKey();
                Map<ScopeRef, ConfigValue> existingInner = nextOverrides.get(key);
                Map<ScopeRef, ConfigValue> newInner = existingInner != null ? new HashMap<>(existingInner) : new HashMap<>();

                if (oc.newValue() == null) {
                    newInner.remove(oc.scope());
                } else {
                    newInner.put(oc.scope(), oc.newValue());
                }

                if (newInner.isEmpty()) {
                    nextOverrides.remove(key);
                } else {
                    nextOverrides.put(key, newInner);
                }
            }
        }

        return new ConfigSnapshot(this.epoch, cs.version(), nextDefs, nextOverrides);
    }

    public ConfigSnapshot forTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return this;
        }

        Map<String, Map<ScopeRef, ConfigValue>> filteredOverrides = new HashMap<>();
        for (Map.Entry<String, Map<ScopeRef, ConfigValue>> entry : overrides.entrySet()) {
            Map<ScopeRef, ConfigValue> scopeMap = entry.getValue();
            Map<ScopeRef, ConfigValue> filteredScopeMap = new HashMap<>();

            for (Map.Entry<ScopeRef, ConfigValue> scopeEntry : scopeMap.entrySet()) {
                ScopeRef scope = scopeEntry.getKey();
                if (scope.type() == ScopeType.GLOBAL || tenantId.equals(scope.tenantId())) {
                    filteredScopeMap.put(scope, scopeEntry.getValue());
                }
            }

            if (!filteredScopeMap.isEmpty()) {
                filteredOverrides.put(entry.getKey(), filteredScopeMap);
            }
        }

        return new ConfigSnapshot(this.epoch, this.version, this.definitions, filteredOverrides);
    }

    public Set<ScopeRef> knownScopes() {
        Set<ScopeRef> scopes = new HashSet<>();
        for (Map<ScopeRef, ConfigValue> map : overrides.values()) {
            scopes.addAll(map.keySet());
        }
        return Collections.unmodifiableSet(scopes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConfigSnapshot that)) return false;
        return version == that.version &&
               Objects.equals(epoch, that.epoch) &&
               Objects.equals(definitions, that.definitions) &&
               Objects.equals(overrides, that.overrides);
    }

    @Override
    public int hashCode() {
        return Objects.hash(epoch, version, definitions, overrides);
    }
}
