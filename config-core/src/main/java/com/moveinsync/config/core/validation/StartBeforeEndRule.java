package com.moveinsync.config.core.validation;

import com.moveinsync.config.core.model.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class StartBeforeEndRule implements CrossFieldRule {
    public static final String START_KEY = "night.window.start_offset";
    public static final String END_KEY = "night.window.end_offset";

    @Override
    public List<Violation> check(ConfigSnapshot candidate, ConfigResolver resolver, Set<ScopeRef> affectedScopes) {
        if (!candidate.definitions().containsKey(START_KEY) || !candidate.definitions().containsKey(END_KEY)) {
            return List.of();
        }

        List<Violation> violations = new ArrayList<>();
        Set<ScopeRef> scopesToCheck = affectedScopes.isEmpty() ? Set.of(ScopeRef.global()) : affectedScopes;

        for (ScopeRef scope : scopesToCheck) {
            ConfigContext ctx = scope.toContext();
            try {
                ConfigValue startVal = resolver.resolveValue(candidate, START_KEY, ctx);
                ConfigValue endVal = resolver.resolveValue(candidate, END_KEY, ctx);

                if (startVal instanceof DurationValue sd && endVal instanceof DurationValue ed) {
                    Duration start = sd.value();
                    Duration end = ed.value();
                    if (!start.minus(end).isNegative()) { // start >= end
                        violations.add(new Violation(
                                "START_OFFSET_NOT_BEFORE_END_OFFSET",
                                START_KEY,
                                "Night window start offset (" + start + ") must be strictly before end offset (" + end + ") at scope " + scope.key()
                        ));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return violations;
    }
}

