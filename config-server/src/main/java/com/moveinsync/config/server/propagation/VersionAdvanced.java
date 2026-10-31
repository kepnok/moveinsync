package com.moveinsync.config.server.propagation;

import java.util.Collections;
import java.util.Set;

public record VersionAdvanced(long version, Set<String> tenants) {
    public VersionAdvanced {
        tenants = tenants != null ? Set.copyOf(tenants) : Collections.emptySet();
    }
}

