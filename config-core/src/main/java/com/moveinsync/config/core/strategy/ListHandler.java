package com.moveinsync.config.core.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.moveinsync.config.core.exception.ValidationFailedException;
import com.moveinsync.config.core.model.ConfigValue;
import com.moveinsync.config.core.model.ListValue;
import com.moveinsync.config.core.model.ValidationRules;
import com.moveinsync.config.core.model.ValueType;
import com.moveinsync.config.core.validation.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListHandler implements ValueTypeHandler {
    private final Map<ValueType, ValueTypeHandler> elementHandlers;

    public ListHandler(Map<ValueType, ValueTypeHandler> elementHandlers) {
        this.elementHandlers = elementHandlers;
    }

    @Override
    public ValueType supports() {
        return ValueType.LIST;
    }

    @Override
    public ConfigValue parse(JsonNode json, ValidationRules rules) throws ValidationFailedException {
        if (json == null || !json.isArray()) {
            throw new ValidationFailedException(List.of(new Violation("INVALID_LIST_TYPE", "value", "List value must be a JSON array")));
        }

        List<Violation> violations = new ArrayList<>();
        if (rules != null && rules.maxItems() != null && json.size() > rules.maxItems()) {
            violations.add(new Violation("LIST_EXCEEDS_MAX_ITEMS", "value",
                    "List size " + json.size() + " exceeds maximum allowed " + rules.maxItems()));
        }

        ValueType elemType = rules != null ? rules.elementType() : null;
        ValueTypeHandler elemHandler = (elemType != null && elementHandlers != null) ? elementHandlers.get(elemType) : null;

        List<ConfigValue> items = new ArrayList<>(json.size());
        for (int i = 0; i < json.size(); i++) {
            JsonNode itemNode = json.get(i);
            if (elemHandler != null) {
                try {
                    items.add(elemHandler.parse(itemNode, null));
                } catch (ValidationFailedException vfe) {
                    for (Violation v : vfe.violations()) {
                        violations.add(new Violation(v.code(), "value[" + i + "]", v.message()));
                    }
                }
            } else {
                // If no specific element handler, guess simple types or enum
                if (itemNode.isNumber()) {
                    items.add(new NumberHandler().parse(itemNode, null));
                } else if (itemNode.isTextual()) {
                    items.add(new EnumHandler().parse(itemNode, null));
                } else {
                    violations.add(new Violation("UNSUPPORTED_LIST_ELEMENT", "value[" + i + "]", "Cannot parse element type"));
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new ValidationFailedException(violations);
        }

        return new ListValue(items);
    }
}
