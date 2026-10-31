package com.moveinsync.config.server.dto;

import com.moveinsync.config.core.model.ScopeRef;
import com.moveinsync.config.core.model.ScopeType;

public record ScopeDto(String type, String id) {
    public ScopeRef toScopeRef() {
        if ("GLOBAL".equalsIgnoreCase(type)) {
            return ScopeRef.global();
        }
        return new ScopeRef(ScopeType.valueOf(type.toUpperCase()), id);
    }
}

