package com.moveinsync.config.server.repository;

import java.util.UUID;

public interface ConfigHeadRepository {
    record Head(long version, UUID epoch) {}

    Head lockForUpdate();
    Head getHead();
    void advance(long newVersion);
    void resetEpoch(UUID newEpoch, long version);
}

