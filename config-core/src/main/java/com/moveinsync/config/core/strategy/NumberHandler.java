package com.moveinsync.config.core.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.moveinsync.config.core.exception.ValidationFailedException;
import com.moveinsync.config.core.model.ConfigValue;
import com.moveinsync.config.core.model.NumberValue;
import com.moveinsync.config.core.model.ValidationRules;
import com.moveinsync.config.core.model.ValueType;
import com.moveinsync.config.core.validation.Violation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class NumberHandler implements ValueTypeHandler {
    @Override
    public ValueType supports() {
        return ValueType.NUMBER;
    }

    @Override
    public ConfigValue parse(JsonNode json, ValidationRules rules) throws ValidationFailedException {
        if (json == null || !json.isNumber()) {
            throw new ValidationFailedException(List.of(new Violation("INVALID_NUMBER_TYPE", "value", "Value must be a valid number")));
        }

        BigDecimal val = json.decimalValue();
        List<Violation> violations = new ArrayList<>();

        if (rules != null) {
            BigDecimal min = rules.min();
            if (min != null && val.compareTo(min) < 0) {
                violations.add(new Violation("NUMBER_BELOW_MIN", "value", "Value " + val + " is below minimum allowed " + min));
            }
            BigDecimal max = rules.max();
            if (max != null && val.compareTo(max) > 0) {
                violations.add(new Violation("NUMBER_ABOVE_MAX", "value", "Value " + val + " exceeds maximum allowed " + max));
            }
        }

        if (!violations.isEmpty()) {
            throw new ValidationFailedException(violations);
        }

        return new NumberValue(val);
    }
}
