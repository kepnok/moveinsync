package com.moveinsync.config.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

public record ValidationRules(JsonNode raw) {
    public ValidationRules {
        if (raw == null) {
            raw = JsonNodeFactory.instance.objectNode();
        }
    }

    public static ValidationRules empty() {
        return new ValidationRules(JsonNodeFactory.instance.objectNode());
    }

    public BigDecimal min() {
        if (raw.hasNonNull("min")) {
            return new BigDecimal(raw.get("min").asText());
        }
        return null;
    }

    public BigDecimal max() {
        if (raw.hasNonNull("max")) {
            return new BigDecimal(raw.get("max").asText());
        }
        return null;
    }

    public Duration minDuration() {
        if (raw.hasNonNull("minDuration")) {
            return Duration.parse(raw.get("minDuration").asText());
        }
        return null;
    }

    public Duration maxDuration() {
        if (raw.hasNonNull("maxDuration")) {
            return Duration.parse(raw.get("maxDuration").asText());
        }
        return null;
    }

    public Set<String> allowedValues() {
        if (raw.hasNonNull("allowedValues") && raw.get("allowedValues").isArray()) {
            Set<String> set = new HashSet<>();
            for (JsonNode n : raw.get("allowedValues")) {
                set.add(n.asText());
            }
            return Set.copyOf(set);
        }
        return Set.of();
    }

    public boolean allowOvernight() {
        if (raw.hasNonNull("allowOvernight")) {
            return raw.get("allowOvernight").asBoolean(false);
        }
        return false;
    }

    public ValueType elementType() {
        if (raw.hasNonNull("elementType")) {
            return ValueType.valueOf(raw.get("elementType").asText().toUpperCase());
        }
        return null;
    }

    public Integer maxItems() {
        if (raw.hasNonNull("maxItems")) {
            return raw.get("maxItems").asInt();
        }
        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ObjectNode node = JsonNodeFactory.instance.objectNode();

        public Builder min(long min) {
            node.put("min", min);
            return this;
        }

        public Builder min(BigDecimal min) {
            node.put("min", min);
            return this;
        }

        public Builder max(long max) {
            node.put("max", max);
            return this;
        }

        public Builder max(BigDecimal max) {
            node.put("max", max);
            return this;
        }

        public Builder minDuration(Duration d) {
            node.put("minDuration", d.toString());
            return this;
        }

        public Builder maxDuration(Duration d) {
            node.put("maxDuration", d.toString());
            return this;
        }

        public Builder allowedValues(String... values) {
            var arr = node.putArray("allowedValues");
            for (String v : values) {
                arr.add(v);
            }
            return this;
        }

        public Builder allowOvernight(boolean allow) {
            node.put("allowOvernight", allow);
            return this;
        }

        public Builder elementType(ValueType type) {
            node.put("elementType", type.name());
            return this;
        }

        public Builder maxItems(int max) {
            node.put("maxItems", max);
            return this;
        }

        public ValidationRules build() {
            return new ValidationRules(node);
        }
    }
}

