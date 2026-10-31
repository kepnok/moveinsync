package com.moveinsync.config.core.validation;

import com.moveinsync.config.core.exception.ValidationFailedException;
import com.moveinsync.config.core.model.*;
import com.moveinsync.config.core.strategy.ValueTypeRegistry;
import java.util.*;

public final class ChangeSetValidator {
    private final ValueTypeRegistry typeRegistry;
    private final List<CrossFieldRule> crossFieldRules;
    private final ConfigResolver resolver;

    public ChangeSetValidator(ValueTypeRegistry typeRegistry, List<CrossFieldRule> crossFieldRules, ConfigResolver resolver) {
        this.typeRegistry = Objects.requireNonNull(typeRegistry, "typeRegistry cannot be null");
        this.crossFieldRules = crossFieldRules != null ? List.copyOf(crossFieldRules) : Collections.emptyList();
        this.resolver = Objects.requireNonNull(resolver, "resolver cannot be null");
    }

    public void validate(ConfigSnapshot current, ChangeSet draft) throws ValidationFailedException {
        Objects.requireNonNull(current, "current snapshot cannot be null");
        Objects.requireNonNull(draft, "draft changeset cannot be null");

        List<Violation> violations = new ArrayList<>();
        Map<String, SettingDefinition> defs = new HashMap<>(current.definitions());
        Set<ScopeRef> affectedScopes = new HashSet<>();
        boolean affectsAllScopes = false;

        // Step 1: Validate individual items and record definition changes
        for (ChangeItem item : draft.items()) {
            if (item instanceof DefinitionChange dc) {
                affectsAllScopes = true;
                SettingDefinition def = dc.newDef();
                if (def.key() == null || def.key().isBlank()) {
                    violations.add(new Violation("EMPTY_SETTING_KEY", "settingKey", "Setting key cannot be empty"));
                }
                defs.put(def.key(), def);
                // Validate default value against rules
                try {
                    typeRegistry.parse(def.type(), def.defaultValue().toJson(), def.rules(), def.key());
                } catch (ValidationFailedException vfe) {
                    violations.addAll(vfe.violations());
                }
            } else if (item instanceof OverrideChange oc) {
                if (oc.scope().type() == ScopeType.GLOBAL) {
                    affectsAllScopes = true;
                } else {
                    affectedScopes.add(oc.scope());
                }

                // Scope syntax checks
                validateScope(oc.scope(), oc.settingKey(), violations);

                // If settingKey was added in this changeset or existing in current
                SettingDefinition def = defs.get(oc.settingKey());
                if (def == null) {
                    violations.add(new Violation("UNKNOWN_SETTING", oc.settingKey(), "Setting '" + oc.settingKey() + "' does not exist"));
                } else if (oc.newValue() != null) {
                    if (oc.newValue().type() != def.type()) {
                        violations.add(new Violation("TYPE_MISMATCH", oc.settingKey(),
                                "Value type " + oc.newValue().type() + " does not match setting type " + def.type()));
                    } else {
                        // Re-validate against setting rules
                        try {
                            typeRegistry.parse(def.type(), oc.newValue().toJson(), def.rules(), oc.settingKey());
                        } catch (ValidationFailedException vfe) {
                            violations.addAll(vfe.violations());
                        }
                    }
                }
            }
        }

        // If there are definition/type errors already, fail fast before building candidate if candidate would be broken
        if (!violations.isEmpty()) {
            throw new ValidationFailedException(violations);
        }

        // Step 2: Build candidate snapshot
        ConfigSnapshot candidate = current.withChangeSet(draft);

        // Step 3: Compute affected scopes + descendants
        Set<ScopeRef> allKnownScopes = candidate.knownScopes();
        Set<ScopeRef> scopesToCheck = new HashSet<>();

        if (affectsAllScopes) {
            scopesToCheck.add(ScopeRef.global());
            scopesToCheck.addAll(allKnownScopes);
        } else {
            scopesToCheck.addAll(affectedScopes);
            for (ScopeRef affected : affectedScopes) {
                for (ScopeRef known : allKnownScopes) {
                    if (isDescendantOf(known, affected)) {
                        scopesToCheck.add(known);
                    }
                }
            }
        }

        // Evaluate cross-field rules
        for (CrossFieldRule rule : crossFieldRules) {
            try {
                List<Violation> ruleViolations = rule.check(candidate, resolver, scopesToCheck);
                if (ruleViolations != null && !ruleViolations.isEmpty()) {
                    violations.addAll(ruleViolations);
                }
            } catch (Exception e) {
                violations.add(new Violation("CROSS_FIELD_RULE_ERROR", "rules", "Error executing cross-field rule: " + e.getMessage()));
            }
        }

        // Step 4: Collect all violations, throw once
        if (!violations.isEmpty()) {
            throw new ValidationFailedException(violations);
        }
    }

    private void validateScope(ScopeRef scope, String settingKey, List<Violation> violations) {
        String id = scope.id();
        switch (scope.type()) {
            case GLOBAL -> {
                // valid
            }
            case CLIENT -> {
                if (id.contains("/")) {
                    violations.add(new Violation("INVALID_CLIENT_SCOPE_ID", settingKey, "CLIENT scope id must not contain slashes: " + id));
                }
            }
            case OFFICE -> {
                int slash = id.indexOf('/');
                if (slash <= 0 || slash == id.length() - 1 || id.indexOf('/', slash + 1) != -1) {
                    violations.add(new Violation("INVALID_OFFICE_SCOPE_ID", settingKey, "OFFICE scope id must be in format <tenant>/<office>: " + id));
                }
            }
            case SHIFT -> {
                String[] parts = id.split("/");
                if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
                    violations.add(new Violation("INVALID_SHIFT_SCOPE_ID", settingKey, "SHIFT scope id must be in format <tenant>/<office>/<shift>: " + id));
                }
            }
        }
    }

    private boolean isDescendantOf(ScopeRef child, ScopeRef parent) {
        if (parent.type() == ScopeType.GLOBAL) return true;
        if (child.type().specificity() <= parent.type().specificity()) return false;
        return child.id().startsWith(parent.id() + "/");
    }
}
