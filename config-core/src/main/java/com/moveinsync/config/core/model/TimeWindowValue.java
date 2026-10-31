package com.moveinsync.config.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

public record TimeWindowValue(LocalTime start, LocalTime end) implements ConfigValue {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public TimeWindowValue {
        Objects.requireNonNull(start, "start time cannot be null");
        Objects.requireNonNull(end, "end time cannot be null");
    }

    public static TimeWindowValue of(LocalTime start, LocalTime end) {
        return new TimeWindowValue(start, end);
    }

    public static TimeWindowValue of(String start, String end) {
        return new TimeWindowValue(LocalTime.parse(start), LocalTime.parse(end));
    }

    public boolean overnight() {
        return end.isBefore(start) || end.equals(start);
    }

    @Override
    public ValueType type() {
        return ValueType.TIME_WINDOW;
    }

    @Override
    public JsonNode toJson() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("start", start.format(FORMATTER));
        node.put("end", end.format(FORMATTER));
        return node;
    }

    @Override
    public Object rawValue() {
        return Map.of("start", start.format(FORMATTER), "end", end.format(FORMATTER));
    }
}
