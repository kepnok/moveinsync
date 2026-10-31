package com.moveinsync.config.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.moveinsync.config.core.exception.ValidationFailedException;
import com.moveinsync.config.core.model.*;
import com.moveinsync.config.core.strategy.ValueTypeRegistry;
import com.moveinsync.config.core.validation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationTest {
    private ValueTypeRegistry registry;
    private ChangeSetValidator validator;
    private ConfigResolver resolver;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        registry = new ValueTypeRegistry();
        resolver = new ConfigResolver();
        List<CrossFieldRule> rules = List.of(
                new StartBeforeEndRule(),
                new MinLessThanMaxRideTimeRule(),
                new CutoffWithinLeadTimeRule()
        );
        validator = new ChangeSetValidator(registry, rules, resolver);
        mapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Number validation: rejects value outside [min, max], e.g. -30 for cut-off 0..1440")
    void testNumberValidation() {
        ValidationRules rules = ValidationRules.builder().min(0).max(1440).build();

        // Valid
        ConfigValue v1 = registry.parse(ValueType.NUMBER, mapper.valueToTree(30), rules);
        assertThat(((NumberValue) v1).asLong()).isEqualTo(30);

        // Invalid: -30
        assertThatThrownBy(() -> registry.parse(ValueType.NUMBER, mapper.valueToTree(-30), rules))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> {
                    var v = ((ValidationFailedException) e).violations();
                    assertThat(v).anyMatch(vi -> vi.code().equals("NUMBER_BELOW_MIN"));
                });

        // Invalid: 1500 (> 1440)
        assertThatThrownBy(() -> registry.parse(ValueType.NUMBER, mapper.valueToTree(1500), rules))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> {
                    var v = ((ValidationFailedException) e).violations();
                    assertThat(v).anyMatch(vi -> vi.code().equals("NUMBER_ABOVE_MAX"));
                });
    }

    @Test
    @DisplayName("Duration validation: rejects negative duration and bounds")
    void testDurationValidation() {
        ValidationRules rules = ValidationRules.builder()
                .minDuration(Duration.ofMinutes(10))
                .maxDuration(Duration.ofHours(3))
                .build();

        // Valid ISO-8601
        ConfigValue v1 = registry.parse(ValueType.DURATION, mapper.valueToTree("PT30M"), rules);
        assertThat(((DurationValue) v1).value()).isEqualTo(Duration.ofMinutes(30));

        // Negative duration
        assertThatThrownBy(() -> registry.parse(ValueType.DURATION, mapper.valueToTree("-PT10M"), rules))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> {
                    var v = ((ValidationFailedException) e).violations();
                    assertThat(v).anyMatch(vi -> vi.code().equals("NEGATIVE_DURATION"));
                });

        // Below min: 5 minutes
        assertThatThrownBy(() -> registry.parse(ValueType.DURATION, mapper.valueToTree("PT5M"), rules))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> {
                    var v = ((ValidationFailedException) e).violations();
                    assertThat(v).anyMatch(vi -> vi.code().equals("DURATION_BELOW_MIN"));
                });
    }

    @Test
    @DisplayName("TimeWindow validation: rejects end <= start when allowOvernight=false, accepts when true")
    void testTimeWindowValidation() {
        ValidationRules normalRules = ValidationRules.builder().allowOvernight(false).build();
        ValidationRules overnightRules = ValidationRules.builder().allowOvernight(true).build();

        var normalWindow = mapper.createObjectNode().put("start", "08:00").put("end", "17:00");
        var invertedWindow = mapper.createObjectNode().put("start", "22:00").put("end", "06:00");
        var equalWindow = mapper.createObjectNode().put("start", "10:00").put("end", "10:00");

        // Normal window valid
        ConfigValue v1 = registry.parse(ValueType.TIME_WINDOW, normalWindow, normalRules);
        assertThat(((TimeWindowValue) v1).overnight()).isFalse();

        // Inverted window invalid with allowOvernight=false
        assertThatThrownBy(() -> registry.parse(ValueType.TIME_WINDOW, invertedWindow, normalRules))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> {
                    var v = ((ValidationFailedException) e).violations();
                    assertThat(v).anyMatch(vi -> vi.code().equals("TIME_WINDOW_ENDS_BEFORE_START"));
                });

        // Equal window invalid with allowOvernight=false
        assertThatThrownBy(() -> registry.parse(ValueType.TIME_WINDOW, equalWindow, normalRules))
                .isInstanceOf(ValidationFailedException.class);

        // Inverted window VALID with allowOvernight=true
        ConfigValue v2 = registry.parse(ValueType.TIME_WINDOW, invertedWindow, overnightRules);
        assertThat(((TimeWindowValue) v2).overnight()).isTrue();
    }

    @Test
    @DisplayName("Enum validation: rejects values not in allowedValues")
    void testEnumValidation() {
        ValidationRules rules = ValidationRules.builder().allowedValues("MANDATORY", "OPTIONAL", "NONE").build();

        // Valid
        ConfigValue v1 = registry.parse(ValueType.ENUM, mapper.valueToTree("MANDATORY"), rules);
        assertThat(((EnumValue) v1).value()).isEqualTo("MANDATORY");

        // Invalid
        assertThatThrownBy(() -> registry.parse(ValueType.ENUM, mapper.valueToTree("CUSTOM"), rules))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> {
                    var v = ((ValidationFailedException) e).violations();
                    assertThat(v).anyMatch(vi -> vi.code().equals("ENUM_VALUE_NOT_ALLOWED"));
                });
    }

    @Test
    @DisplayName("List validation: enforces max items and element types")
    void testListValidation() {
        ValidationRules rules = ValidationRules.builder()
                .elementType(ValueType.NUMBER)
                .maxItems(3)
                .build();

        var validArr = mapper.createArrayNode().add(10).add(20);
        var tooLongArr = mapper.createArrayNode().add(1).add(2).add(3).add(4);
        var wrongTypeArr = mapper.createArrayNode().add(10).add("not-a-number");

        // Valid
        ConfigValue v1 = registry.parse(ValueType.LIST, validArr, rules);
        assertThat(((ListValue) v1).items()).hasSize(2);

        // Exceeds max items
        assertThatThrownBy(() -> registry.parse(ValueType.LIST, tooLongArr, rules))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> {
                    var v = ((ValidationFailedException) e).violations();
                    assertThat(v).anyMatch(vi -> vi.code().equals("LIST_EXCEEDS_MAX_ITEMS"));
                });

        // Wrong element type
        assertThatThrownBy(() -> registry.parse(ValueType.LIST, wrongTypeArr, rules))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    @DisplayName("CrossFieldRule: StartBeforeEndRule rejects start_offset >= end_offset")
    void testStartBeforeEndCrossFieldRule() {
        SettingDefinition startDef = new SettingDefinition(
                StartBeforeEndRule.START_KEY,
                ValueType.DURATION,
                DurationValue.parse("PT20H"),
                ValidationRules.empty(),
                "Start offset"
        );
        SettingDefinition endDef = new SettingDefinition(
                StartBeforeEndRule.END_KEY,
                ValueType.DURATION,
                DurationValue.parse("PT22H"),
                ValidationRules.empty(),
                "End offset"
        );

        ConfigSnapshot snapshot = new ConfigSnapshot(
                UUID.randomUUID(), 1L,
                Map.of(startDef.key(), startDef, endDef.key(), endDef),
                Map.of()
        );

        // Try setting start_offset = 23H at OFFICE level (end is 22H from default) -> start >= end!
        ChangeSet invalidCs = new ChangeSet(
                2L, UUID.randomUUID(), ChangeKind.NORMAL, "author", "test", null,
                List.of(new OverrideChange(StartBeforeEndRule.START_KEY, ScopeRef.office("acme/blr-1"), null, DurationValue.parse("PT23H"))),
                null, null
        );

        assertThatThrownBy(() -> validator.validate(snapshot, invalidCs))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> {
                    var v = ((ValidationFailedException) e).violations();
                    assertThat(v).anyMatch(vi -> vi.code().equals("START_OFFSET_NOT_BEFORE_END_OFFSET"));
                });
    }

    @Test
    @DisplayName("Validator collects ALL violations and never throws partial")
    void testCollectAllViolations() {
        SettingDefinition cutoffDef = new SettingDefinition(
                "booking.cutoff",
                ValueType.NUMBER,
                NumberValue.of(60),
                ValidationRules.builder().min(0).max(1440).build(),
                "Cutoff"
        );
        ConfigSnapshot snapshot = new ConfigSnapshot(UUID.randomUUID(), 1L, Map.of(cutoffDef.key(), cutoffDef), Map.of());

        // Two bad items: one negative cutoff (-30), one unknown setting
        ChangeSet badCs = new ChangeSet(
                2L, UUID.randomUUID(), ChangeKind.NORMAL, "author", "test", null,
                List.of(
                        new OverrideChange("booking.cutoff", ScopeRef.office("acme/blr-1"), null, NumberValue.of(-30)),
                        new OverrideChange("unknown.setting", ScopeRef.office("acme/blr-1"), null, NumberValue.of(10))
                ),
                null, null
        );

        assertThatThrownBy(() -> validator.validate(snapshot, badCs))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> {
                    var v = ((ValidationFailedException) e).violations();
                    assertThat(v).hasSizeGreaterThanOrEqualTo(2);
                    assertThat(v).anyMatch(vi -> vi.code().equals("NUMBER_BELOW_MIN"));
                    assertThat(v).anyMatch(vi -> vi.code().equals("UNKNOWN_SETTING"));
                });
    }
}
