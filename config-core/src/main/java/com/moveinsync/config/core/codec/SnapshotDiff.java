package com.moveinsync.config.core.codec;

import com.moveinsync.config.core.model.ConfigSnapshot;
import com.moveinsync.config.core.model.ConfigValue;
import com.moveinsync.config.core.model.ScopeRef;
import java.util.*;

public final class SnapshotDiff {
    private SnapshotDiff() {}

    public static List<DiffEntry> diff(ConfigSnapshot from, ConfigSnapshot to) {
        Objects.requireNonNull(from, "from snapshot cannot be null");
        Objects.requireNonNull(to, "to snapshot cannot be null");

        List<DiffEntry> entries = new ArrayList<>();
        Set<String> allKeys = new HashSet<>(from.overrides().keySet());
        allKeys.addAll(to.overrides().keySet());

        for (String key : allKeys) {
            Map<ScopeRef, ConfigValue> fromMap = from.overrides().getOrDefault(key, Collections.emptyMap());
            Map<ScopeRef, ConfigValue> toMap = to.overrides().getOrDefault(key, Collections.emptyMap());

            Set<ScopeRef> allScopes = new HashSet<>(fromMap.keySet());
            allScopes.addAll(toMap.keySet());

            for (ScopeRef scope : allScopes) {
                ConfigValue before = fromMap.get(scope);
                ConfigValue after = toMap.get(scope);
                if (!Objects.equals(before, after)) {
                    entries.add(new DiffEntry(key, scope, before, after));
                }
            }
        }

        // Sort entries deterministically by key, then scope
        entries.sort(Comparator.comparing(DiffEntry::settingKey).thenComparing(e -> e.scope().key()));
        return entries;
    }
}
