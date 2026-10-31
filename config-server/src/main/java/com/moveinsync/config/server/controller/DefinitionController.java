package com.moveinsync.config.server.controller;

import com.moveinsync.config.core.model.ConfigValue;
import com.moveinsync.config.core.model.SettingDefinition;
import com.moveinsync.config.core.model.ValidationRules;
import com.moveinsync.config.core.model.ValueType;
import com.moveinsync.config.core.strategy.ValueTypeRegistry;
import com.moveinsync.config.server.dto.CreateDefinitionRequest;
import com.moveinsync.config.server.dto.PublishResponse;
import com.moveinsync.config.server.exception.ForbiddenException;
import com.moveinsync.config.server.security.AuthPrincipal;
import com.moveinsync.config.server.security.Role;
import com.moveinsync.config.server.security.TenantGuard;
import com.moveinsync.config.server.service.DefinitionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/api/v1/definitions")
public class DefinitionController {
    private final DefinitionService definitionService;
    private final ValueTypeRegistry typeRegistry;
    private final TenantGuard tenantGuard;

    public DefinitionController(DefinitionService definitionService, ValueTypeRegistry typeRegistry, TenantGuard tenantGuard) {
        this.definitionService = definitionService;
        this.typeRegistry = typeRegistry;
        this.tenantGuard = tenantGuard;
    }

    @GetMapping
    public ResponseEntity<Collection<SettingDefinition>> getAll(@AuthenticationPrincipal AuthPrincipal principal) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required");
        }
        return ResponseEntity.ok(definitionService.findAll());
    }

    @GetMapping("/{key}")
    public ResponseEntity<SettingDefinition> getByKey(
            @PathVariable("key") String key,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required");
        }
        return ResponseEntity.ok(definitionService.findByKey(key));
    }

    @PostMapping
    public ResponseEntity<PublishResponse> create(
            @RequestBody CreateDefinitionRequest req,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return upsert(req, principal);
    }

    @PutMapping
    public ResponseEntity<PublishResponse> update(
            @RequestBody CreateDefinitionRequest req,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return upsert(req, principal);
    }

    private ResponseEntity<PublishResponse> upsert(CreateDefinitionRequest req, AuthPrincipal principal) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required");
        }
        tenantGuard.assertRole(principal, Role.ADMIN);

        ValueType type = ValueType.valueOf(req.type().toUpperCase());
        ValidationRules rules = new ValidationRules(req.rules());
        ConfigValue defaultValue = typeRegistry.parse(type, req.defaultValue(), rules, req.key());
        SettingDefinition def = new SettingDefinition(req.key(), type, defaultValue, rules, req.description());

        PublishResponse resp = definitionService.upsertDefinition(def, req.reason(), principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }
}

