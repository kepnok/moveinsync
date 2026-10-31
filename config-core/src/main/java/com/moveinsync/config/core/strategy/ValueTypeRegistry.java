package com.moveinsync.config.core.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.moveinsync.config.core.exception.ValidationFailedException;
import com.moveinsync.config.core.model.ConfigValue;
import com.moveinsync.config.core.model.SettingDefinition;
import com.moveinsync.config.core.model.ValidationRules;
import com.moveinsync.config.core.model.ValueType;
import com.moveinsync.config.core.validation.Violation;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ValueTypeRegistry {
    private final Map<ValueType, ValueTypeHandler> handlers = new EnumMap<>(ValueType.class);

    public ValueTypeRegistry() {
        register(new NumberHandler());
        register(new DurationHandler());
        register(new TimeWindowHandler());
        register(new EnumHandler());
        register(new ListHandler(handlers));
    }

    public void register(ValueTypeHandler handler) {
        Objects.requireNonNull(handler, "handler cannot be null");
        handlers.put(handler.supports(), handler);
    }

    public ConfigValue parse(SettingDefinition def, JsonNode json) throws ValidationFailedException {
        Objects.requireNonNull(def, "SettingDefinition cannot be null");
        return parse(def.type(), json, def.rules(), def.key());
    }

    public ConfigValue parse(ValueType type, JsonNode json, ValidationRules rules) throws ValidationFailedException {
        return parse(type, json, rules, "value");
    }

    public ConfigValue parse(ValueType type, JsonNode json, ValidationRules rules, String fieldName) throws ValidationFailedException {
        ValueTypeHandler handler = handlers.get(type);
        if (handler == null) {
            throw new ValidationFailedException(List.of(new Violation("UNSUPPORTED_VALUE_TYPE", fieldName, "No handler for type: " + type)));
        }
        try {
            return handler.parse(json, rules);
        } catch (ValidationFailedException vfe) {
            List<Violation> mapped = vfe.violations().stream()
                    .map(v -> new Violation(v.code(), fieldName, v.message()))
                    .toList();
            throw new ValidationFailedException(mapped);
        }
    }
}
