package com.moveinsync.config.core.model;

import java.util.Objects;

public record ScopeRef(ScopeType type, String id) implements Comparable<ScopeRef> {
    private static final ScopeRef GLOBAL_INSTANCE = new ScopeRef(ScopeType.GLOBAL, "global");

    public ScopeRef {
        Objects.requireNonNull(type, "ScopeType cannot be null");
        if (type == ScopeType.GLOBAL && (id == null || id.isBlank())) {
            id = "global";
        }
        Objects.requireNonNull(id, "Scope id cannot be null");
    }

    public static ScopeRef global() {
        return GLOBAL_INSTANCE;
    }

    public static ScopeRef client(String clientId) {
        return new ScopeRef(ScopeType.CLIENT, clientId);
    }

    public static ScopeRef office(String officeId) {
        return new ScopeRef(ScopeType.OFFICE, officeId);
    }

    public static ScopeRef shift(String shiftId) {
        return new ScopeRef(ScopeType.SHIFT, shiftId);
    }

    public String key() {
        return type.name() + ":" + id;
    }

    public String tenantId() {
        if (type == ScopeType.GLOBAL) {
            return null;
        }
        int slashIdx = id.indexOf('/');
        return slashIdx > 0 ? id.substring(0, slashIdx) : id;
    }

    public static ScopeRef parse(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Scope key cannot be empty");
        }
        int colonIdx = key.indexOf(':');
        if (colonIdx <= 0) {
            if ("GLOBAL".equalsIgnoreCase(key)) {
                return global();
            }
            throw new IllegalArgumentException("Invalid scope key format (expected TYPE:id): " + key);
        }
        String typeStr = key.substring(0, colonIdx);
        String idStr = key.substring(colonIdx + 1);
        ScopeType st = ScopeType.valueOf(typeStr.toUpperCase());
        return new ScopeRef(st, idStr);
    }

    public ConfigContext toContext() {
        return switch (type) {
            case GLOBAL -> ConfigContext.of(null, null, null);
            case CLIENT -> ConfigContext.of(id, null, null);
            case OFFICE -> ConfigContext.of(tenantId(), id, null);
            case SHIFT -> {
                int lastSlash = id.lastIndexOf('/');
                String officeId = lastSlash > 0 ? id.substring(0, lastSlash) : null;
                yield ConfigContext.of(tenantId(), officeId, id);
            }
        };
    }

    @Override
    public int compareTo(ScopeRef o) {
        int c = Integer.compare(o.type.specificity(), this.type.specificity()); // higher specificity first
        if (c != 0) return c;
        return this.id.compareTo(o.id);
    }
}
