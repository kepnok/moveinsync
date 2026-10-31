package com.moveinsync.config.core.validation;

import com.moveinsync.config.core.model.ConfigResolver;
import com.moveinsync.config.core.model.ConfigSnapshot;
import com.moveinsync.config.core.model.ScopeRef;
import java.util.List;
import java.util.Set;

public interface CrossFieldRule {
    List<Violation> check(ConfigSnapshot candidate, ConfigResolver resolver, Set<ScopeRef> affectedScopes);
}

