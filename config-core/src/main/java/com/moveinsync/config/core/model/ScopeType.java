package com.moveinsync.config.core.model;

public enum ScopeType {
    GLOBAL, CLIENT, OFFICE, SHIFT;

    public int specificity() {
        return switch (this) {
            case SHIFT -> 3;
            case OFFICE -> 2;
            case CLIENT -> 1;
            case GLOBAL -> 0;
        };
    }

    public ScopeType parent() {
        return switch (this) {
            case SHIFT -> OFFICE;
            case OFFICE -> CLIENT;
            case CLIENT -> GLOBAL;
            case GLOBAL -> null;
        };
    }
}
