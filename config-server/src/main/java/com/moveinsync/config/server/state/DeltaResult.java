package com.moveinsync.config.server.state;

import com.moveinsync.config.core.model.ChangeSet;
import java.util.List;

public sealed interface DeltaResult permits DeltaResult.Delta, DeltaResult.FullRequired {
    record Delta(long fromVersion, long toVersion, List<ChangeSet> changeSets) implements DeltaResult {}
    record FullRequired(long headVersion) implements DeltaResult {}
}

