package com.moveinsync.config.server.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class JwtService {
    private final SecretKey key;
    private final long defaultExpirationMs;

    public JwtService(
            @Value("${config.jwt.secret:MoveInSyncHierarchicalTenantConfigSuperSecretKey2026!#}") String secret,
            @Value("${config.jwt.expiration-ms:86400000}") long defaultExpirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.defaultExpirationMs = defaultExpirationMs;
    }

    public String mint(String subject, Set<Role> roles, Set<String> tenants) {
        return mint(subject, roles, tenants, defaultExpirationMs);
    }

    public String mint(String subject, Set<Role> roles, Set<String> tenants, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        List<String> roleStrings = roles.stream().map(Role::name).toList();
        List<String> tenantList = new ArrayList<>(tenants);

        return Jwts.builder()
                .subject(subject)
                .claim("roles", roleStrings)
                .claim("tenants", tenantList)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    @SuppressWarnings("unchecked")
    public AuthPrincipal verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String sub = claims.getSubject();
            List<String> roleStrings = claims.get("roles", List.class);
            List<String> tenantStrings = claims.get("tenants", List.class);

            Set<Role> roles = (roleStrings != null)
                    ? roleStrings.stream().map(Role::valueOf).collect(Collectors.toSet())
                    : Collections.emptySet();

            Set<String> tenants = (tenantStrings != null)
                    ? new HashSet<>(tenantStrings)
                    : Collections.emptySet();

            return new AuthPrincipal(sub, roles, tenants);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT token: " + e.getMessage(), e);
        }
    }
}

