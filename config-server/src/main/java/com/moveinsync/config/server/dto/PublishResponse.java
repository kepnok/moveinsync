package com.moveinsync.config.server.dto;

import java.util.List;
import java.util.UUID;

public record PublishResponse(
        long version,
        UUID changeSetId,
        String status,
        List<DiffEntryDto> diff
) {
    public static PublishResponse committed(long version, UUID changeSetId, List<DiffEntryDto> diff) {
        return new PublishResponse(version, changeSetId, "COMMITTED", diff);
    }

    public static PublishResponse noChange(long currentVersion) {
        return new PublishResponse(currentVersion, null, "NO_CHANGE", List.of());
    }

    public static PublishResponse dryRun(long currentVersion, List<DiffEntryDto> diff) {
        return new PublishResponse(currentVersion, null, "DRY_RUN", diff);
    }
}

