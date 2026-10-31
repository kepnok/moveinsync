package com.moveinsync.config.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import java.math.BigDecimal;
import java.util.Objects;

public record NumberValue(BigDecimal value) implements ConfigValue {
    public NumberValue {
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static NumberValue of(long val) {
        return new NumberValue(BigDecimal.valueOf(val));
    }

    public static NumberValue of(double val) {
        return new NumberValue(BigDecimal.valueOf(val));
    }

    public static NumberValue of(BigDecimal val) {
        return new NumberValue(val);
    }

    public long asLong() {
        return value.longValue();
    }

    public double asDouble() {
        return value.doubleValue();
    }

    @Override
    public ValueType type() {
        return ValueType.NUMBER;
    }

    @Override
    public JsonNode toJson() {
        return new DecimalNode(value);
    }

    @Override
    public Object rawValue() {
        return value;
    }
}

