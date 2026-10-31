package com.moveinsync.config.core.validation;

import com.moveinsync.config.core.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CutoffWithinLeadTimeRule implements CrossFieldRule {
    public static final String CUTOFF_KEY = "booking.cutoff";
    public static final String LEAD_TIME_KEY = "booking.lead_time";

    @Override
    public List<Violation> check(ConfigSnapshot candidate, ConfigResolver resolver, Set<ScopeRef> affectedScopes) {
        if (!candidate.definitions().containsKey(CUTOFF_KEY) || !candidate.definitions().containsKey(LEAD_TIME_KEY)) {
            return List.of();
        }

        List<Violation> violations = new ArrayList<>();
        Set<ScopeRef> scopesToCheck = affectedScopes.isEmpty() ? Set.of(ScopeRef.global()) : affectedScopes;

        for (ScopeRef scope : scopesToCheck) {
            ConfigContext ctx = scope.toContext();
            try {
                ConfigValue cutoffVal = resolver.resolveValue(candidate, CUTOFF_KEY, ctx);
                ConfigValue leadVal = resolver.resolveValue(candidate, LEAD_TIME_KEY, ctx);

                if (cutoffVal instanceof NumberValue cd && leadVal instanceof NumberValue ld) {
                    if (cd.value().compareTo(ld.value()) > 0) { // cutoff > lead_time
                        violations.add(new Violation(
                                "CUTOFF_EXCEEDS_LEAD_TIME",
                                CUTOFF_KEY,
                                "Booking cut-off (" + cd.value() + ") cannot exceed booking lead time (" + ld.value() + ") at scope " + scope.key()
                        ));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return violations;
    }
}
