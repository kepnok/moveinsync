package com.moveinsync.config.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.moveinsync.config.core.codec.DiffEntry;
import com.moveinsync.config.core.codec.SnapshotCodec;
import com.moveinsync.config.core.codec.SnapshotDiff;
import com.moveinsync.config.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigSnapshotTest {
    private UUID epoch;
    private SettingDefinition cutoffDef;
    private SettingDefinition escortDef;
    private ScopeRef global;
    private ScopeRef acmeOffice1;
    private ScopeRef acmeOffice2;
    private ScopeRef globexOffice;

    @BeforeEach
    void setUp() {
        epoch = UUID.randomUUID();
        cutoffDef = new SettingDefinition(
                "booking.cutoff",
                ValueType.NUMBER,
                NumberValue.of(60),
                ValidationRules.builder().min(0).max(1440).build(),
                "Cutoff"
        );
        escortDef = new SettingDefinition(
                "escort.required",
                ValueType.ENUM,
                EnumValue.of("NONE"),
                ValidationRules.builder().allowedValues("MANDATORY", "OPTIONAL", "NONE").build(),
                "Escort rule"
        );
        global = ScopeRef.global();
        acmeOffice1 = ScopeRef.office("acme/blr-1");
        acmeOffice2 = ScopeRef.office("acme/blr-2");
        globexOffice = ScopeRef.office("globex/del-1");
    }

    @Test
    @DisplayName("Copy-on-write does not mutate the source snapshot")
    void testCopyOnWriteImmutability() {
        ConfigSnapshot initial = new ConfigSnapshot(
                epoch, 1L,
                Map.of(cutoffDef.key(), cutoffDef),
                Map.of("booking.cutoff", Map.of(global, NumberValue.of(60), acmeOffice1, NumberValue.of(30)))
        );

        ChangeSet cs = new ChangeSet(
                2L, UUID.randomUUID(), ChangeKind.NORMAL, "operator", "update blr-1 and add blr-2", null,
                List.of(
                        new OverrideChange("booking.cutoff", acmeOffice1, NumberValue.of(30), NumberValue.of(15)),
                        new OverrideChange("booking.cutoff", acmeOffice2, null, NumberValue.of(45))
                ),
                null, null
        );

        ConfigSnapshot updated = initial.withChangeSet(cs);

        // Initial snapshot remains untouched
        assertThat(initial.version()).isEqualTo(1L);
        assertThat(initial.overrideAt("booking.cutoff", acmeOffice1)).contains(NumberValue.of(30));
        assertThat(initial.overrideAt("booking.cutoff", acmeOffice2)).isEmpty();

        // Updated snapshot has new values
        assertThat(updated.version()).isEqualTo(2L);
        assertThat(updated.overrideAt("booking.cutoff", acmeOffice1)).contains(NumberValue.of(15));
        assertThat(updated.overrideAt("booking.cutoff", acmeOffice2)).contains(NumberValue.of(45));
    }

    @Test
    @DisplayName("forTenant leaks no other tenant's overrides, keeps GLOBAL and definitions")
    void testTenantIsolationInSnapshot() {
        ConfigSnapshot full = new ConfigSnapshot(
                epoch, 5L,
                Map.of(cutoffDef.key(), cutoffDef, escortDef.key(), escortDef),
                Map.of(
                        "booking.cutoff", Map.of(
                                global, NumberValue.of(60),
                                acmeOffice1, NumberValue.of(30),
                                globexOffice, NumberValue.of(40)
                        ),
                        "escort.required", Map.of(
                                acmeOffice2, EnumValue.of("MANDATORY"),
                                globexOffice, EnumValue.of("OPTIONAL")
                        )
                )
        );

        // Filter for "acme"
        ConfigSnapshot acmeSnap = full.forTenant("acme");

        // Definitions must be fully preserved
        assertThat(acmeSnap.definitions()).containsKey("booking.cutoff");
        assertThat(acmeSnap.definitions()).containsKey("escort.required");

        // ACME overrides are preserved
        assertThat(acmeSnap.overrideAt("booking.cutoff", global)).contains(NumberValue.of(60));
        assertThat(acmeSnap.overrideAt("booking.cutoff", acmeOffice1)).contains(NumberValue.of(30));
        assertThat(acmeSnap.overrideAt("escort.required", acmeOffice2)).contains(EnumValue.of("MANDATORY"));

        // GLOBEX overrides MUST NOT exist in acmeSnap
        assertThat(acmeSnap.overrideAt("booking.cutoff", globexOffice)).isEmpty();
        assertThat(acmeSnap.overrideAt("escort.required", globexOffice)).isEmpty();

        // And vice versa for globex
        ConfigSnapshot globexSnap = full.forTenant("globex");
        assertThat(globexSnap.overrideAt("booking.cutoff", globexOffice)).contains(NumberValue.of(40));
        assertThat(globexSnap.overrideAt("booking.cutoff", acmeOffice1)).isEmpty();
        assertThat(globexSnap.overrideAt("escort.required", acmeOffice2)).isEmpty();
    }

    @Test
    @DisplayName("knownScopes returns all scopes present across overrides")
    void testKnownScopes() {
        ConfigSnapshot s = new ConfigSnapshot(
                epoch, 1L,
                Map.of(cutoffDef.key(), cutoffDef),
                Map.of("booking.cutoff", Map.of(global, NumberValue.of(60), acmeOffice1, NumberValue.of(30)))
        );

        Set<ScopeRef> scopes = s.knownScopes();
        assertThat(scopes).containsExactlyInAnyOrder(global, acmeOffice1);
    }

    @Test
    @DisplayName("SnapshotDiff correctly computes added, modified, deleted diffs")
    void testSnapshotDiff() {
        ConfigSnapshot s1 = new ConfigSnapshot(
                epoch, 1L,
                Map.of(cutoffDef.key(), cutoffDef),
                Map.of("booking.cutoff", Map.of(global, NumberValue.of(60), acmeOffice1, NumberValue.of(30)))
        );

        ConfigSnapshot s2 = new ConfigSnapshot(
                epoch, 2L,
                Map.of(cutoffDef.key(), cutoffDef),
                Map.of("booking.cutoff", Map.of(global, NumberValue.of(60), acmeOffice1, NumberValue.of(15), acmeOffice2, NumberValue.of(45)))
        );

        List<DiffEntry> diffs = SnapshotDiff.diff(s1, s2);
        assertThat(diffs).hasSize(2);

        // acmeOffice1 modified 30 -> 15
        DiffEntry d1 = diffs.stream().filter(d -> d.scope().equals(acmeOffice1)).findFirst().orElseThrow();
        assertThat(d1.before()).isEqualTo(NumberValue.of(30));
        assertThat(d1.after()).isEqualTo(NumberValue.of(15));

        // acmeOffice2 added null -> 45
        DiffEntry d2 = diffs.stream().filter(d -> d.scope().equals(acmeOffice2)).findFirst().orElseThrow();
        assertThat(d2.before()).isNull();
        assertThat(d2.after()).isEqualTo(NumberValue.of(45));
    }

    @Test
    @DisplayName("SnapshotCodec encode and decode roundtrip")
    void testSnapshotCodec() {
        SnapshotCodec codec = new SnapshotCodec();
        ConfigSnapshot original = new ConfigSnapshot(
                epoch, 42L,
                Map.of(cutoffDef.key(), cutoffDef, escortDef.key(), escortDef),
                Map.of("booking.cutoff", Map.of(global, NumberValue.of(60), acmeOffice1, NumberValue.of(30)))
        );

        byte[] encoded = codec.encode(original);
        ConfigSnapshot decoded = codec.decode(encoded);

        assertThat(decoded.epoch()).isEqualTo(original.epoch());
        assertThat(decoded.version()).isEqualTo(original.version());
        assertThat(decoded.definitions()).hasSize(2);
        assertThat(decoded.overrideAt("booking.cutoff", acmeOffice1)).contains(NumberValue.of(30));
        assertThat(decoded.overrideAt("booking.cutoff", global)).contains(NumberValue.of(60));

        // Test ChangeSet encoding and decoding
        ChangeSet cs = new ChangeSet(
                43L, UUID.randomUUID(), ChangeKind.NORMAL, "author-1", "reason-1", null,
                List.of(
                        new DefinitionChange("new.setting", null, cutoffDef),
                        new OverrideChange("booking.cutoff", acmeOffice1, NumberValue.of(30), NumberValue.of(10))
                ),
                null, null
        );

        JsonNode csJson = codec.encode(cs);
        ChangeSet decodedCs = codec.decodeChangeSet(csJson, original.definitions());

        assertThat(decodedCs.version()).isEqualTo(cs.version());
        assertThat(decodedCs.authorId()).isEqualTo(cs.authorId());
        assertThat(decodedCs.items()).hasSize(2);
    }
}

