package com.moveinsync.config.core.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class ConfigContext {
    private final String clientId;
    private final String officeId;
    private final String shiftId;
    private final ScopeRef[] chain;

    private ConfigContext(String clientId, String officeId, String shiftId) {
        this.clientId = clientId;
        this.officeId = officeId;
        this.shiftId = shiftId;

        List<ScopeRef> scopes = new ArrayList<>(4);
        if (shiftId != null && !shiftId.isBlank()) {
            scopes.add(new ScopeRef(ScopeType.SHIFT, shiftId));
        }
        if (officeId != null && !officeId.isBlank()) {
            scopes.add(new ScopeRef(ScopeType.OFFICE, officeId));
        }
        if (clientId != null && !clientId.isBlank()) {
            scopes.add(new ScopeRef(ScopeType.CLIENT, clientId));
        }
        scopes.add(ScopeRef.global());

        this.chain = scopes.toArray(new ScopeRef[0]);
    }

    public static ConfigContext of(String clientId, String officeId, String shiftId) {
        return new ConfigContext(clientId, officeId, shiftId);
    }

    public static ConfigContext ofClient(String clientId) {
        return new ConfigContext(clientId, null, null);
    }

    public static ConfigContext ofOffice(String clientId, String officeId) {
        return new ConfigContext(clientId, officeId, null);
    }

    public String clientId() {
        return clientId;
    }

    public String officeId() {
        return officeId;
    }

    public String shiftId() {
        return shiftId;
    }

    public ScopeRef[] chain() {
        return chain;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConfigContext that)) return false;
        return Objects.equals(clientId, that.clientId) &&
               Objects.equals(officeId, that.officeId) &&
               Objects.equals(shiftId, that.shiftId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId, officeId, shiftId);
    }

    @Override
    public String toString() {
        return "ConfigContext[clientId=" + clientId + ", officeId=" + officeId + ", shiftId=" + shiftId + "]";
    }
}
