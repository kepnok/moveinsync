package com.moveinsync.config.server.propagation;

import org.springframework.stereotype.Component;

@Component
public class ConfigEventBus {
    private final SseConnectionRegistry connectionRegistry;

    public ConfigEventBus(SseConnectionRegistry connectionRegistry) {
        this.connectionRegistry = connectionRegistry;
    }

    public void publish(VersionAdvanced event) {
        connectionRegistry.broadcast(event);
    }
}

