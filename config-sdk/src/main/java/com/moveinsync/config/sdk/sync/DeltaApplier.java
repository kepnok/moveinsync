package com.moveinsync.config.sdk.sync;

import com.moveinsync.config.core.model.ChangeSet;
import com.moveinsync.config.core.model.ConfigSnapshot;
import com.moveinsync.config.sdk.api.SyncResponse;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class DeltaApplier {

    public sealed interface ApplyResult permits ApplyResult.Updated, ApplyResult.Ignored, ApplyResult.ResyncRequired {
        record Updated(ConfigSnapshot candidate) implements ApplyResult {}
        record Ignored(String reason) implements ApplyResult {}
        record ResyncRequired(String reason) implements ApplyResult {}
    }

    public ApplyResult apply(ConfigSnapshot current, SyncResponse response) {
        Objects.requireNonNull(response, "SyncResponse cannot be null");

        if (current == null) {
            if ("FULL".equalsIgnoreCase(response.mode()) && response.snapshot() != null) {
                return new ApplyResult.Updated(response.snapshot());
            }
            return new ApplyResult.ResyncRequired("No initial snapshot; full snapshot required");
        }

        if ("FULL".equalsIgnoreCase(response.mode())) {
            ConfigSnapshot incoming = response.snapshot();
            if (incoming == null) {
                return new ApplyResult.Ignored("Null snapshot payload in FULL response");
            }

            // Correction 7: On epoch change, accept full snapshot and reset version
            if (!incoming.epoch().equals(current.epoch())) {
                return new ApplyResult.Updated(incoming);
            }

            // Same epoch: never move backwards!
            if (incoming.version() < current.version()) {
                return new ApplyResult.Ignored("FULL snapshot version " + incoming.version() + " is older than current " + current.version());
            }

            return new ApplyResult.Updated(incoming);
        } else if ("DELTA".equalsIgnoreCase(response.mode())) {
            // Delta response
            if (!response.epoch().equals(current.epoch())) {
                return new ApplyResult.ResyncRequired("Epoch mismatch: client " + current.epoch() + " vs server " + response.epoch());
            }

            long toVersion = response.toVersion() != null ? response.toVersion() : current.version();
            long fromVersion = response.fromVersion() != null ? response.fromVersion() : 0L;

            if (toVersion <= current.version()) {
                return new ApplyResult.Ignored("Delta toVersion " + toVersion + " <= current version " + current.version());
            }

            if (fromVersion > current.version()) {
                // Gap in versions
                return new ApplyResult.ResyncRequired("Gap detected: delta fromVersion " + fromVersion + " > current version " + current.version());
            }

            List<ChangeSet> changeSets = response.changeSets();
            if (changeSets == null || changeSets.isEmpty()) {
                // No changes
                return new ApplyResult.Ignored("Empty change sets in delta");
            }

            // Sort by version ascending
            List<ChangeSet> sorted = changeSets.stream()
                    .sorted(Comparator.comparingLong(ChangeSet::version))
                    .toList();

            ConfigSnapshot candidate = current;
            for (ChangeSet cs : sorted) {
                if (cs.version() <= candidate.version()) {
                    continue; // Skip already applied items
                }
                candidate = candidate.withChangeSet(cs);
            }

            return new ApplyResult.Updated(candidate);
        }

        return new ApplyResult.Ignored("Unknown sync response mode: " + response.mode());
    }
}
