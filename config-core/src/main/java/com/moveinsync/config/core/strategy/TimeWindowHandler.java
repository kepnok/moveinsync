package com.moveinsync.config.core.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.moveinsync.config.core.exception.ValidationFailedException;
import com.moveinsync.config.core.model.ConfigValue;
import com.moveinsync.config.core.model.TimeWindowValue;
import com.moveinsync.config.core.model.ValidationRules;
import com.moveinsync.config.core.model.ValueType;
import com.moveinsync.config.core.validation.Violation;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class TimeWindowHandler implements ValueTypeHandler {
    @Override
    public ValueType supports() {
        return ValueType.TIME_WINDOW;
    }

    @Override
    public ConfigValue parse(JsonNode json, ValidationRules rules) throws ValidationFailedException {
        if (json == null || !json.isObject()) {
            throw new ValidationFailedException(List.of(new Violation("INVALID_TIME_WINDOW_TYPE", "value", "Time window must be a JSON object with 'start' and 'end'")));
        }

        JsonNode startNode = json.get("start");
        JsonNode endNode = json.get("end");
        if (startNode == null || endNode == null || !startNode.isTextual() || !endNode.isTextual()) {
            throw new ValidationFailedException(List.of(new Violation("MALFORMED_TIME_WINDOW", "value", "Time window must contain string fields 'start' and 'end'")));
        }

        LocalTime start;
        LocalTime end;
        try {
            start = LocalTime.parse(startNode.asText());
            end = LocalTime.parse(endNode.asText());
        } catch (DateTimeParseException e) {
            throw new ValidationFailedException(List.of(new Violation("MALFORMED_TIME_FORMAT", "value", "Time must be in format HH:mm or HH:mm:ss")));
        }

        boolean allowOvernight = rules != null && rules.allowOvernight();
        List<Violation> violations = new ArrayList<>();

        if (!allowOvernight && !end.isAfter(start)) {
            violations.add(new Violation("TIME_WINDOW_ENDS_BEFORE_START", "value",
                    "Time window end (" + end + ") must be strictly after start (" + start + ") when overnight windows are not permitted"));
        }

        if (!violations.isEmpty()) {
            throw new ValidationFailedException(violations);
        }

        return new TimeWindowValue(start, end);
    }
}
