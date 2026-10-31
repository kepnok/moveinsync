package com.moveinsync.config.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.time.Duration;
import java.util.Objects;

public record DurationValue(Duration value) implements ConfigValue {
    public DurationValue {
        Objects.requireNonNull(value, "Duration value cannot be null");
    }

    public static DurationValue ofMinutes(long minutes) {
        return new DurationValue(Duration.ofMinutes(minutes));
    }

    public static DurationValue ofHours(long hours) {
        return new DurationValue(Duration.ofHours(hours));
    }

    public static DurationValue ofSeconds(long seconds) {
        return new DurationValue(Duration.ofSeconds(seconds));
    }

    public static DurationValue parse(String iso8601) {
        return new DurationValue(Duration.parse(iso8601));
    }

    @Override
    public ValueType type() {
        return ValueType.DURATION;
    }

    @Override
    public JsonNode toJson() {
        return new TextNode(value.toString());
    }

    @Override
    public Object rawValue() {
        return value;
    }
}

