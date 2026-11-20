package za.co.mongala.legal.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import za.co.mongala.legal.model.Role;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JWT issuing/validation (Assignment 2 section 11: OAuth2 bearer tokens with
 * scopes such as read:client, write:case, admin:model). The role determines
 * the scope set embedded in the token.
 */
public final class JwtUtil {

    private final SecretKey key;
    private final long tokenTtlMillis;

    public JwtUtil(String secret, int tokenHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.tokenTtlMillis = tokenHours * 3600_000L;
    }

    public String issue(String userId, String name, String email, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("uid", userId)
                .claim("name", name)
                .claim("role", role.name())
                .claim("scopes", scopesFor(role).stream().toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(tokenTtlMillis)))
                .signWith(key)
                .compact();
    }

    /** @return null when the token is invalid/expired. */
    public Session parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            @SuppressWarnings("unchecked")
            List<String> scopes = claims.get("scopes", List.class);
            return new Session(
                    claims.get("uid", String.class),
                    claims.getSubject(),
                    claims.get("name", String.class),
                    Role.valueOf(claims.get("role", String.class)),
                    scopes == null ? Set.of() : Set.copyOf(scopes));
        } catch (Exception e) {
            return null;
        }
    }

    /** Scope sets per role (NFR1 role-based access control). */
    public static Set<String> scopesFor(Role role) {
        return switch (role) {
            case LAWYER -> Set.of("read:case", "write:case", "read:audit", "write:audit",
                    "read:client", "write:client");
            case CLIENT -> Set.of("read:client", "write:client");
            case ADMIN -> Set.of("read:case", "write:case", "read:audit", "write:audit",
                    "read:client", "write:client", "admin:model");
        };
    }

    public record Session(String userId, String email, String name, Role role, Set<String> scopes) {
        public boolean has(String scope) { return scopes.contains(scope); }
        public String roleName() { return role.getDisplayName(); }
    }
}
