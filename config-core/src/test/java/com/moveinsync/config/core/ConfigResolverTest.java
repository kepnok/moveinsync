package com.moveinsync.config.core;

import com.moveinsync.config.core.exception.UnknownSettingException;
import com.moveinsync.config.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigResolverTest {
    private ConfigResolver resolver;
    private SettingDefinition cutoffDef;
    private SettingDefinition rideTimeDef;
    private UUID epoch;

    @BeforeEach
    void setUp() {
        resolver = new ConfigResolver();
        epoch = UUID.randomUUID();
        cutoffDef = new SettingDefinition(
                "booking.cutoff",
                ValueType.NUMBER,
                NumberValue.of(60),
                ValidationRules.builder().min(0).max(1440).build(),
                "Cut-off time in minutes"
        );
        rideTimeDef = new SettingDefinition(
                "max.ride.time",
                ValueType.DURATION,
                DurationValue.ofMinutes(120),
                ValidationRules.empty(),
                "Maximum ride time"
        );
    }

    @Test
    @DisplayName("Should fall back to default when no overrides exist")
    void shouldFallbackToDefault() {
        ConfigSnapshot snapshot = new ConfigSnapshot(epoch, 1L, Map.of(cutoffDef.key(), cutoffDef), Map.of());
        ConfigContext ctx = ConfigContext.of("acme", "acme/blr-1", "acme/blr-1/night");

        ResolvedConfig resolved = resolver.resolve(snapshot, "booking.cutoff", ctx);
        assertThat(resolved.fromDefault()).isTrue();
        assertThat(resolved.value()).isEqualTo(NumberValue.of(60));
        assertThat(resolved.source()).isEqualTo(ScopeRef.global());

        ConfigValue fastVal = resolver.resolveValue(snapshot, "booking.cutoff", ctx);
        assertThat(fastVal).isEqualTo(NumberValue.of(60));
    }

    @Test
    @DisplayName("Hierarchy: SHIFT beats OFFICE beats CLIENT beats GLOBAL beats default")
    void shouldResolveMostSpecificScope() {
        ScopeRef global = ScopeRef.global();
        ScopeRef client = ScopeRef.client("acme");
        ScopeRef office = ScopeRef.office("acme/blr-1");
        ScopeRef shift = ScopeRef.shift("acme/blr-1/night");

        // 1. Only GLOBAL override (50)
        ConfigSnapshot s1 = new ConfigSnapshot(epoch, 1L, Map.of(cutoffDef.key(), cutoffDef),
                Map.of("booking.cutoff", Map.of(global, NumberValue.of(50))));
        ConfigContext ctx = ConfigContext.of("acme", "acme/blr-1", "acme/blr-1/night");
        assertThat(resolver.resolve(s1, "booking.cutoff", ctx).value()).isEqualTo(NumberValue.of(50));
        assertThat(resolver.resolve(s1, "booking.cutoff", ctx).fromDefault()).isFalse();
        assertThat(resolver.resolve(s1, "booking.cutoff", ctx).source()).isEqualTo(global);

        // 2. Add CLIENT override (45) -> beats GLOBAL
        ConfigSnapshot s2 = new ConfigSnapshot(epoch, 2L, Map.of(cutoffDef.key(), cutoffDef),
                Map.of("booking.cutoff", Map.of(global, NumberValue.of(50), client, NumberValue.of(45))));
        assertThat(resolver.resolve(s2, "booking.cutoff", ctx).value()).isEqualTo(NumberValue.of(45));
        assertThat(resolver.resolve(s2, "booking.cutoff", ctx).source()).isEqualTo(client);

        // 3. Add OFFICE override (30) -> beats CLIENT
        ConfigSnapshot s3 = new ConfigSnapshot(epoch, 3L, Map.of(cutoffDef.key(), cutoffDef),
                Map.of("booking.cutoff", Map.of(global, NumberValue.of(50), client, NumberValue.of(45), office, NumberValue.of(30))));
        assertThat(resolver.resolve(s3, "booking.cutoff", ctx).value()).isEqualTo(NumberValue.of(30));
        assertThat(resolver.resolve(s3, "booking.cutoff", ctx).source()).isEqualTo(office);

        // 4. Add SHIFT override (15) -> beats OFFICE
        ConfigSnapshot s4 = new ConfigSnapshot(epoch, 4L, Map.of(cutoffDef.key(), cutoffDef),
                Map.of("booking.cutoff", Map.of(global, NumberValue.of(50), client, NumberValue.of(45), office, NumberValue.of(30), shift, NumberValue.of(15))));
        assertThat(resolver.resolve(s4, "booking.cutoff", ctx).value()).isEqualTo(NumberValue.of(15));
        assertThat(resolver.resolve(s4, "booking.cutoff", ctx).source()).isEqualTo(shift);

        // Another office under acme should see CLIENT (45) since no office override exists for blr-2
        ConfigContext ctx2 = ConfigContext.of("acme", "acme/blr-2", null);
        assertThat(resolver.resolve(s4, "booking.cutoff", ctx2).value()).isEqualTo(NumberValue.of(45));
        assertThat(resolver.resolve(s4, "booking.cutoff", ctx2).source()).isEqualTo(client);

        // Another tenant globex should see GLOBAL (50)
        ConfigContext ctxGlobex = ConfigContext.of("globex", "globex/del-1", null);
        assertThat(resolver.resolve(s4, "booking.cutoff", ctxGlobex).value()).isEqualTo(NumberValue.of(50));
        assertThat(resolver.resolve(s4, "booking.cutoff", ctxGlobex).source()).isEqualTo(global);
    }

    @Test
    @DisplayName("Deleting override falls back cleanly to parent level")
    void shouldFallbackWhenOverrideDeleted() {
        ScopeRef client = ScopeRef.client("acme");
        ScopeRef office = ScopeRef.office("acme/blr-1");

        ConfigSnapshot s = new ConfigSnapshot(epoch, 1L, Map.of(cutoffDef.key(), cutoffDef),
                Map.of("booking.cutoff", Map.of(client, NumberValue.of(45), office, NumberValue.of(30))));

        ConfigContext ctx = ConfigContext.of("acme", "acme/blr-1", null);
        assertThat(resolver.resolve(s, "booking.cutoff", ctx).value()).isEqualTo(NumberValue.of(30));

        // Delete office override
        ChangeSet cs = new ChangeSet(2L, UUID.randomUUID(), ChangeKind.NORMAL, "operator", "delete office override",
                null, java.util.List.of(new OverrideChange("booking.cutoff", office, NumberValue.of(30), null)), null, null);

        ConfigSnapshot sNext = s.withChangeSet(cs);
        assertThat(resolver.resolve(sNext, "booking.cutoff", ctx).value()).isEqualTo(NumberValue.of(45));
        assertThat(resolver.resolve(sNext, "booking.cutoff", ctx).source()).isEqualTo(client);
    }

    @Test
    @DisplayName("Explain should list all chain levels and mark winning level as applied")
    void shouldExplainResolution() {
        ScopeRef global = ScopeRef.global();
        ScopeRef client = ScopeRef.client("acme");
        ScopeRef office = ScopeRef.office("acme/blr-1");

        ConfigSnapshot s = new ConfigSnapshot(epoch, 1L, Map.of(cutoffDef.key(), cutoffDef),
                Map.of("booking.cutoff", Map.of(global, NumberValue.of(60), office, NumberValue.of(30))));

        ConfigContext ctx = ConfigContext.of("acme", "acme/blr-1", "acme/blr-1/night");
        ExplainResult explain = resolver.explain(s, "booking.cutoff", ctx);

        assertThat(explain.effective().value()).isEqualTo(NumberValue.of(30));
        assertThat(explain.effective().source()).isEqualTo(office);

        // Chain is [SHIFT, OFFICE, CLIENT, GLOBAL]
        assertThat(explain.levels()).hasSize(4);
        assertThat(explain.levels().get(0).scope()).isEqualTo(ScopeRef.shift("acme/blr-1/night"));
        assertThat(explain.levels().get(0).valueOrNull()).isNull();
        assertThat(explain.levels().get(0).applied()).isFalse();

        assertThat(explain.levels().get(1).scope()).isEqualTo(office);
        assertThat(explain.levels().get(1).valueOrNull()).isEqualTo(NumberValue.of(30));
        assertThat(explain.levels().get(1).applied()).isTrue();

        assertThat(explain.levels().get(2).scope()).isEqualTo(client);
        assertThat(explain.levels().get(2).valueOrNull()).isNull();
        assertThat(explain.levels().get(2).applied()).isFalse();

        assertThat(explain.levels().get(3).scope()).isEqualTo(global);
        assertThat(explain.levels().get(3).valueOrNull()).isEqualTo(NumberValue.of(60));
        assertThat(explain.levels().get(3).applied()).isFalse();
    }

    @Test
    @DisplayName("Unknown setting should throw UnknownSettingException")
    void shouldThrowOnUnknownSetting() {
        ConfigSnapshot snapshot = ConfigSnapshot.empty(epoch);
        ConfigContext ctx = ConfigContext.of("acme", null, null);

        assertThatThrownBy(() -> resolver.resolve(snapshot, "non.existent", ctx))
                .isInstanceOf(UnknownSettingException.class)
                .hasMessageContaining("non.existent");

        assertThatThrownBy(() -> resolver.resolveValue(snapshot, "non.existent", ctx))
                .isInstanceOf(UnknownSettingException.class);
    }
}

