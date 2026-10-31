package com.moveinsync.config.server.controller;

import com.moveinsync.config.server.security.JwtService;
import com.moveinsync.config.server.security.Role;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/dev")
public class DevTokenController {
    private final JwtService jwtService;

    public DevTokenController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public record TokenRequest(String subject, Set<String> roles, Set<String> tenants) {}

    @PostMapping("/token")
    public ResponseEntity<Map<String, String>> mintToken(@RequestBody TokenRequest req) {
        String sub = (req.subject() != null && !req.subject().isBlank()) ? req.subject() : "dev-user";
        Set<Role> roles = (req.roles() != null && !req.roles().isEmpty())
                ? req.roles().stream().map(r -> Role.valueOf(r.toUpperCase())).collect(Collectors.toSet())
                : Set.of(Role.ADMIN, Role.OPERATOR);
        Set<String> tenants = (req.tenants() != null && !req.tenants().isEmpty())
                ? req.tenants()
                : Set.of("*");

        String token = jwtService.mint(sub, roles, tenants);
        return ResponseEntity.ok(Map.of("token", token));
    }
}

