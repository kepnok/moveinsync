package com.moveinsync.config.server.service;

import com.moveinsync.config.core.model.ConfigSnapshot;
import com.moveinsync.config.server.dto.VersionResponseDto;
import com.moveinsync.config.server.state.ServerStateCache;
import org.springframework.stereotype.Service;

@Service
public class VersionService {
    private final ServerStateCache stateCache;

    public VersionService(ServerStateCache stateCache) {
        this.stateCache = stateCache;
    }

    public VersionResponseDto headVersion(String tenantId) {
        ConfigSnapshot head = stateCache.head();
        return new VersionResponseDto(head.epoch(), head.version());
    }
}

