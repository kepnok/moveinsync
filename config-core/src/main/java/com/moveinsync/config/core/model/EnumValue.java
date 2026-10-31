package com.moveinsync.config.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Objects;

public record EnumValue(String value) implements ConfigValue {
    public EnumValue {
        Objects.requireNonNull(value, "Enum value cannot be null");
    }

    public static EnumValue of(String value) {
        return new EnumValue(value);
    }

    @Override
    public ValueType type() {
        return ValueType.ENUM;
    }

    @Override
    public JsonNode toJson() {
        return new TextNode(value);
    }

    @Override
    public Object rawValue() {
        return value;
    }
}
