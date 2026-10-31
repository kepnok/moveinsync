package com.moveinsync.config.core.model;

import com.moveinsync.config.core.exception.UnknownSettingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ConfigResolver {

    public ResolvedConfig resolve(ConfigSnapshot snapshot, String key, ConfigContext ctx) {
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(ctx, "ctx cannot be null");

        SettingDefinition def = snapshot.definitions().get(key);
        if (def == null) {
            throw new UnknownSettingException(key);
        }

        Map<ScopeRef, ConfigValue> scopeMap = snapshot.overrides().get(key);
        if (scopeMap != null && !scopeMap.isEmpty()) {
            for (ScopeRef scope : ctx.chain()) {
                ConfigValue value = scopeMap.get(scope);
                if (value != null) {
                    return new ResolvedConfig(key, value, scope, false, snapshot.version());
                }
            }
        }

        return new ResolvedConfig(key, def.defaultValue(), ScopeRef.global(), true, snapshot.version());
    }

    public ConfigValue resolveValue(ConfigSnapshot snapshot, String key, ConfigContext ctx) {
        // Zero-allocation hot path
        SettingDefinition def = snapshot.definitions().get(key);
        if (def == null) {
            throw new UnknownSettingException(key);
        }

        Map<ScopeRef, ConfigValue> scopeMap = snapshot.overrides().get(key);
        if (scopeMap != null) {
            ScopeRef[] chain = ctx.chain();
            for (int i = 0; i < chain.length; i++) {
                ConfigValue value = scopeMap.get(chain[i]);
                if (value != null) {
                    return value;
                }
            }
        }

        return def.defaultValue();
    }

    public ExplainResult explain(ConfigSnapshot snapshot, String key, ConfigContext ctx) {
        ResolvedConfig resolved = resolve(snapshot, key, ctx);
        Map<ScopeRef, ConfigValue> scopeMap = snapshot.overrides().get(key);

        List<LevelEntry> levels = new ArrayList<>(ctx.chain().length);
        for (ScopeRef scope : ctx.chain()) {
            ConfigValue val = (scopeMap != null) ? scopeMap.get(scope) : null;
            boolean applied = !resolved.fromDefault() && scope.equals(resolved.source());
            levels.add(new LevelEntry(scope, val, applied));
        }

        return new ExplainResult(resolved, levels);
    }
}

