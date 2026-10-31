package com.moveinsync.config.core.validation;

import com.moveinsync.config.core.model.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MinLessThanMaxRideTimeRule implements CrossFieldRule {
    public static final String MIN_KEY = "min.ride.time";
    public static final String MAX_KEY = "max.ride.time";

    @Override
    public List<Violation> check(ConfigSnapshot candidate, ConfigResolver resolver, Set<ScopeRef> affectedScopes) {
        if (!candidate.definitions().containsKey(MIN_KEY) || !candidate.definitions().containsKey(MAX_KEY)) {
            return List.of();
        }

        List<Violation> violations = new ArrayList<>();
        Set<ScopeRef> scopesToCheck = affectedScopes.isEmpty() ? Set.of(ScopeRef.global()) : affectedScopes;

        for (ScopeRef scope : scopesToCheck) {
            ConfigContext ctx = scope.toContext();
            try {
                ConfigValue minVal = resolver.resolveValue(candidate, MIN_KEY, ctx);
                ConfigValue maxVal = resolver.resolveValue(candidate, MAX_KEY, ctx);

                if (minVal instanceof DurationValue md && maxVal instanceof DurationValue xd) {
                    Duration min = md.value();
                    Duration max = xd.value();
                    if (!min.minus(max).isNegative()) { // min >= max
                        violations.add(new Violation(
                                "MIN_RIDE_TIME_NOT_LESS_THAN_MAX",
                                MIN_KEY,
                                "Minimum ride time (" + min + ") must be strictly less than maximum ride time (" + max + ") at scope " + scope.key()
                        ));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return violations;
    }
}

