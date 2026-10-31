package com.moveinsync.config.sdk.api;

import com.moveinsync.config.core.model.ChangeSet;
import com.moveinsync.config.core.model.ConfigSnapshot;
import java.util.List;
import java.util.UUID;

public record SyncResponse(
        String mode,
        UUID epoch,
        Long fromVersion,
        Long toVersion,
        Long version,
        List<ChangeSet> changeSets,
        ConfigSnapshot snapshot
) {}
