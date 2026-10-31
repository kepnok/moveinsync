package com.moveinsync.config.sdk;

import com.moveinsync.config.core.exception.UnknownSettingException;
import com.moveinsync.config.core.model.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ConfigView {
    private final ConfigSnapshot snapshot;
    private final ConfigResolver resolver;

    public ConfigView(ConfigSnapshot snapshot, ConfigResolver resolver) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot cannot be null");
        this.resolver = Objects.requireNonNull(resolver, "resolver cannot be null");
    }

    public long getLong(String key, ConfigContext ctx) {
        ConfigValue val = resolver.resolveValue(snapshot, key, ctx);
        if (val instanceof NumberValue num) {
            return num.asLong();
        }
        throw new IllegalStateException("Setting '" + key + "' is type " + (val != null ? val.type() : "null") + ", not NUMBER");
    }

    public Duration getDuration(String key, ConfigContext ctx) {
        ConfigValue val = resolver.resolveValue(snapshot, key, ctx);
        if (val instanceof DurationValue dur) {
            return dur.value();
        }
        throw new IllegalStateException("Setting '" + key + "' is type " + (val != null ? val.type() : "null") + ", not DURATION");
    }

    public TimeWindowValue getTimeWindow(String key, ConfigContext ctx) {
        ConfigValue val = resolver.resolveValue(snapshot, key, ctx);
        if (val instanceof TimeWindowValue tw) {
            return tw;
        }
        throw new IllegalStateException("Setting '" + key + "' is type " + (val != null ? val.type() : "null") + ", not TIME_WINDOW");
    }

    public String getString(String key, ConfigContext ctx) {
        ConfigValue val = resolver.resolveValue(snapshot, key, ctx);
        if (val instanceof EnumValue ev) {
            return ev.value();
        }
        if (val != null) {
            return val.toString();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getList(String key, ConfigContext ctx, Class<T> elemType) {
        ConfigValue val = resolver.resolveValue(snapshot, key, ctx);
        if (val instanceof ListValue lv) {
            List<T> result = new ArrayList<>(lv.items().size());
            for (ConfigValue item : lv.items()) {
                if (elemType == Long.class && item instanceof NumberValue nv) {
                    result.add((T) Long.valueOf(nv.asLong()));
                } else if (elemType == Double.class && item instanceof NumberValue nv) {
                    result.add((T) Double.valueOf(nv.asDouble()));
                } else if (elemType == String.class && item instanceof EnumValue ev) {
                    result.add((T) ev.value());
                } else if (elemType.isInstance(item)) {
                    result.add((T) item);
                } else {
                    throw new IllegalStateException("List item " + item + " cannot be converted to " + elemType.getName());
                }
            }
            return Collections.unmodifiableList(result);
        }
        throw new IllegalStateException("Setting '" + key + "' is type " + (val != null ? val.type() : "null") + ", not LIST");
    }

    public Optional<ConfigValue> get(String key, ConfigContext ctx) {
        try {
            return Optional.ofNullable(resolver.resolveValue(snapshot, key, ctx));
        } catch (UnknownSettingException e) {
            return Optional.empty();
        }
    }

    public ExplainResult explain(String key, ConfigContext ctx) {
        return resolver.explain(snapshot, key, ctx);
    }

    public long version() {
        return snapshot.version();
    }

    public ConfigSnapshot snapshot() {
        return snapshot;
    }
}
