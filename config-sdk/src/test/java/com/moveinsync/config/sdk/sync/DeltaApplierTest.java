package com.moveinsync.config.sdk.sync;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.moveinsync.config.core.model.*;
import com.moveinsync.config.sdk.api.SyncResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DeltaApplierTest {

    private final DeltaApplier applier = new DeltaApplier();

    @Test
    void initialFullSnapshotAppliesSuccessfully() {
        UUID epoch = UUID.randomUUID();
        ConfigSnapshot full = ConfigSnapshot.empty(epoch);

        SyncResponse response = new SyncResponse("FULL", epoch, 0L, 0L, 0L, List.of(), full);
        DeltaApplier.ApplyResult result = applier.apply(null, response);

        assertThat(result).isInstanceOf(DeltaApplier.ApplyResult.Updated.class);
        assertThat(((DeltaApplier.ApplyResult.Updated) result).candidate().epoch()).isEqualTo(epoch);
    }

    @Test
    void deltaAppliesInMonotonicOrder() {
        UUID epoch = UUID.randomUUID();
        SettingDefinition def = new SettingDefinition(
                "cutoff",
                ValueType.NUMBER,
                new NumberValue(new BigDecimal("60")),
                new ValidationRules(JsonNodeFactory.instance.objectNode()),
                "Cutoff"
        );
        ConfigSnapshot initial = ConfigSnapshot.empty(epoch).withChangeSet(
                new ChangeSet(1L, UUID.randomUUID(), ChangeKind.DEFINITION, "sys", "init", Instant.now(),
                        List.of(new DefinitionChange("cutoff", null, def)), null, null)
        );

        // Delta 1 -> 2: override at CLIENT:acme
        ChangeSet cs2 = new ChangeSet(2L, UUID.randomUUID(), ChangeKind.NORMAL, "dev", "override", Instant.now(),
                List.of(new OverrideChange("cutoff", ScopeRef.parse("CLIENT:acme"), null, new NumberValue(new BigDecimal("45")))),
                null, null);

        SyncResponse response = new SyncResponse("DELTA", epoch, 1L, 2L, 2L, List.of(cs2), null);
        DeltaApplier.ApplyResult result = applier.apply(initial, response);

        assertThat(result).isInstanceOf(DeltaApplier.ApplyResult.Updated.class);
        ConfigSnapshot updated = ((DeltaApplier.ApplyResult.Updated) result).candidate();
        assertThat(updated.version()).isEqualTo(2L);
        assertThat(updated.overrideAt("cutoff", ScopeRef.parse("CLIENT:acme")))
                .contains(new NumberValue(new BigDecimal("45")));
    }

    @Test
    void gapInDeltaRequestsFullResync() {
        UUID epoch = UUID.randomUUID();
        ConfigSnapshot current = ConfigSnapshot.empty(epoch); // version 0

        // Delta response claims changes fromVersion 5 to 6 -> gap!
        ChangeSet cs = new ChangeSet(6L, UUID.randomUUID(), ChangeKind.NORMAL, "dev", "gap", Instant.now(),
                List.of(), null, null);
        SyncResponse response = new SyncResponse("DELTA", epoch, 5L, 6L, 6L, List.of(cs), null);

        DeltaApplier.ApplyResult result = applier.apply(current, response);
        assertThat(result).isInstanceOf(DeltaApplier.ApplyResult.ResyncRequired.class);
    }

    @Test
    void epochMismatchInDeltaRequestsFullResync() {
        UUID epoch1 = UUID.randomUUID();
        UUID epoch2 = UUID.randomUUID();
        ConfigSnapshot current = ConfigSnapshot.empty(epoch1);

        SyncResponse response = new SyncResponse("DELTA", epoch2, 0L, 1L, 1L, List.of(), null);
        DeltaApplier.ApplyResult result = applier.apply(current, response);
        assertThat(result).isInstanceOf(DeltaApplier.ApplyResult.ResyncRequired.class);
    }

    @Test
    void fullSnapshotWithNewEpochResetsState() {
        UUID epoch1 = UUID.randomUUID();
        UUID epoch2 = UUID.randomUUID();
        ConfigSnapshot current = ConfigSnapshot.empty(epoch1);

        ConfigSnapshot freshServerSnap = ConfigSnapshot.empty(epoch2);
        SyncResponse response = new SyncResponse("FULL", epoch2, 0L, 0L, 0L, List.of(), freshServerSnap);

        DeltaApplier.ApplyResult result = applier.apply(current, response);
        assertThat(result).isInstanceOf(DeltaApplier.ApplyResult.Updated.class);
        assertThat(((DeltaApplier.ApplyResult.Updated) result).candidate().epoch()).isEqualTo(epoch2);
    }

    @Test
    void olderVersionInSameEpochIsIgnored() {
        UUID epoch = UUID.randomUUID();
        ChangeSet cs5 = new ChangeSet(5L, UUID.randomUUID(), ChangeKind.NORMAL, "sys", "init", Instant.now(),
                List.of(), null, null);
        ConfigSnapshot current = ConfigSnapshot.empty(epoch).withChangeSet(cs5);

        // Stale delta to version 3
        SyncResponse response = new SyncResponse("DELTA", epoch, 1L, 3L, 3L, List.of(), null);
        DeltaApplier.ApplyResult result = applier.apply(current, response);
        assertThat(result).isInstanceOf(DeltaApplier.ApplyResult.Ignored.class);
    }
}
