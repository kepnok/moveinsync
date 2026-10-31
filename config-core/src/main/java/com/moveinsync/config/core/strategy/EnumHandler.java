package com.moveinsync.config.core.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.moveinsync.config.core.exception.ValidationFailedException;
import com.moveinsync.config.core.model.ConfigValue;
import com.moveinsync.config.core.model.EnumValue;
import com.moveinsync.config.core.model.ValidationRules;
import com.moveinsync.config.core.model.ValueType;
import com.moveinsync.config.core.validation.Violation;
import java.util.List;
import java.util.Set;

public class EnumHandler implements ValueTypeHandler {
    @Override
    public ValueType supports() {
        return ValueType.ENUM;
    }

    @Override
    public ConfigValue parse(JsonNode json, ValidationRules rules) throws ValidationFailedException {
        if (json == null || !json.isTextual()) {
            throw new ValidationFailedException(List.of(new Violation("INVALID_ENUM_TYPE", "value", "Enum value must be a string")));
        }

        String str = json.asText();
        if (rules != null) {
            Set<String> allowed = rules.allowedValues();
            if (allowed != null && !allowed.isEmpty() && !allowed.contains(str)) {
                throw new ValidationFailedException(List.of(new Violation("ENUM_VALUE_NOT_ALLOWED", "value",
                        "Value '" + str + "' is not permitted. Allowed: " + allowed)));
            }
        }

        return new EnumValue(str);
    }
}

