package com.moveinsync.config.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record ListValue(List<ConfigValue> items) implements ConfigValue {
    public ListValue {
        Objects.requireNonNull(items, "List items cannot be null");
        items = List.copyOf(items);
    }

    public static ListValue of(List<ConfigValue> items) {
        return new ListValue(items);
    }

    public static ListValue empty() {
        return new ListValue(Collections.emptyList());
    }

    @Override
    public ValueType type() {
        return ValueType.LIST;
    }

    @Override
    public JsonNode toJson() {
        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        for (ConfigValue item : items) {
            arr.add(item.toJson());
        }
        return arr;
    }

    @Override
    public Object rawValue() {
        return items.stream().map(ConfigValue::rawValue).toList();
    }
}
