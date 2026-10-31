package com.moveinsync.config.core.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.moveinsync.config.core.exception.ValidationFailedException;
import com.moveinsync.config.core.model.ConfigValue;
import com.moveinsync.config.core.model.DurationValue;
import com.moveinsync.config.core.model.ValidationRules;
import com.moveinsync.config.core.model.ValueType;
import com.moveinsync.config.core.validation.Violation;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class DurationHandler implements ValueTypeHandler {
    @Override
    public ValueType supports() {
        return ValueType.DURATION;
    }

    @Override
    public ConfigValue parse(JsonNode json, ValidationRules rules) throws ValidationFailedException {
        if (json == null || (!json.isTextual() && !json.isNumber())) {
            throw new ValidationFailedException(List.of(new Violation("INVALID_DURATION_TYPE", "value", "Duration must be an ISO-8601 string (e.g. PT30M) or numeric seconds")));
        }

        Duration duration;
        try {
            if (json.isNumber()) {
                duration = Duration.ofSeconds(json.asLong());
            } else {
                duration = Duration.parse(json.asText());
            }
        } catch (DateTimeParseException e) {
            throw new ValidationFailedException(List.of(new Violation("MALFORMED_DURATION", "value", "Cannot parse duration: " + json.asText())));
        }

        List<Violation> violations = new ArrayList<>();
        if (duration.isNegative()) {
            violations.add(new Violation("NEGATIVE_DURATION", "value", "Duration cannot be negative"));
        }

        if (rules != null) {
            Duration min = rules.minDuration();
            if (min != null && duration.compareTo(min) < 0) {
                violations.add(new Violation("DURATION_BELOW_MIN", "value", "Duration " + duration + " is less than minimum " + min));
            }
            Duration max = rules.maxDuration();
            if (max != null && duration.compareTo(max) > 0) {
                violations.add(new Violation("DURATION_ABOVE_MAX", "value", "Duration " + duration + " exceeds maximum " + max));
            }
        }

        if (!violations.isEmpty()) {
            throw new ValidationFailedException(violations);
        }

        return new DurationValue(duration);
    }
}

