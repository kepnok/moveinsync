package com.moveinsync.config.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public sealed interface ConfigValue permits NumberValue, DurationValue, TimeWindowValue, EnumValue, ListValue {
    ValueType type();
    JsonNode toJson();
    Object rawValue();
}
