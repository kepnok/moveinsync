package com.moveinsync.config.core.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.moveinsync.config.core.exception.ValidationFailedException;
import com.moveinsync.config.core.model.ConfigValue;
import com.moveinsync.config.core.model.ValidationRules;
import com.moveinsync.config.core.model.ValueType;

public interface ValueTypeHandler {
    ValueType supports();
    ConfigValue parse(JsonNode json, ValidationRules rules) throws ValidationFailedException;
}

