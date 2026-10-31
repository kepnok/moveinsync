package com.moveinsync.config.server.service;

import com.moveinsync.config.core.model.*;
import com.moveinsync.config.server.state.ServerStateCache;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ResolveService {
    private final ServerStateCache stateCache;
    private final ConfigResolver resolver;

    public ResolveService(ServerStateCache stateCache, ConfigResolver resolver) {
        this.stateCache = stateCache;
        this.resolver = resolver;
    }

    public record ResolveResponse(
            String tenantId,
            String officeId,
            String shiftId,
            long version,
            Map<String, ResolvedConfig> configs,
            Map<String, ExplainResult> explanations
    ) {}

    public ResolveResponse resolve(
            String tenantId,
            String officeId,
            String shiftId,
            List<String> keys,
            boolean explain
    ) {
        ConfigSnapshot head = stateCache.head();
        ConfigContext ctx = ConfigContext.of(tenantId, officeId, shiftId);

        Set<String> targetKeys;
        if (keys != null && !keys.isEmpty()) {
            targetKeys = new LinkedHashSet<>(keys);
        } else {
            targetKeys = head.definitions().keySet();
        }

        Map<String, ResolvedConfig> configs = new LinkedHashMap<>();
        Map<String, ExplainResult> explanations = explain ? new LinkedHashMap<>() : Collections.emptyMap();

        for (String key : targetKeys) {
            if (head.definitions().containsKey(key)) {
                if (explain) {
                    ExplainResult exp = resolver.explain(head, key, ctx);
                    explanations.put(key, exp);
                    configs.put(key, exp.effective());
                } else {
                    ResolvedConfig res = resolver.resolve(head, key, ctx);
                    configs.put(key, res);
                }
            }
        }

        return new ResolveResponse(tenantId, officeId, shiftId, head.version(), configs, explanations);
    }
}

