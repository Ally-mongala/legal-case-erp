package za.co.mongala.legal.backend.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.mongala.legal.model.Role;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for authentication and authorisation (NFR-03, FR-07).
 *
 * Covers PBKDF2 password storage and the JWT scope model that enforces
 * role-based access control across the REST API.
 */
class SecurityTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256!!";

    // ---------------------------------------------------------------- PBKDF2

    @Test
    @DisplayName("TC-U10: a correct password verifies against its stored hash")
    void correctPasswordVerifies() {
        String stored = PasswordHasher.hash("lawyer123");
        assertTrue(PasswordHasher.verify("lawyer123", stored));
    }

    @Test
    @DisplayName("TC-U11: an incorrect password is rejected")
    void wrongPasswordRejected() {
        String stored = PasswordHasher.hash("lawyer123");
        assertFalse(PasswordHasher.verify("lawyer124", stored));
        assertFalse(PasswordHasher.verify("", stored));
        assertFalse(PasswordHasher.verify("LAWYER123", stored), "verification is case-sensitive");
    }

    @Test
    @DisplayName("TC-U12: the plaintext password never appears in the stored hash")
    void hashDoesNotLeakPlaintext() {
        String password = "MyS3cretPassw0rd";
        String stored = PasswordHasher.hash(password);

        assertFalse(stored.contains(password),
                "stored credential must not contain the plaintext password");
        assertTrue(stored.startsWith("120000$"),
                "stored format is iterations$salt$hash with 120 000 iterations");
    }

    @Test
    @DisplayName("TC-U13: the same password yields a different hash each time (per-user salt)")
    void saltMakesHashesUnique() {
        String a = PasswordHasher.hash("samePassword");
        String b = PasswordHasher.hash("samePassword");

        assertNotEquals(a, b, "a random salt must make the two stored values differ");
        assertTrue(PasswordHasher.verify("samePassword", a));
        assertTrue(PasswordHasher.verify("samePassword", b));
    }

    @Test
    @DisplayName("TC-U14: a malformed stored credential fails safely rather than throwing")
    void malformedHashFailsClosed() {
        assertFalse(PasswordHasher.verify("anything", "not-a-valid-hash"));
        assertFalse(PasswordHasher.verify("anything", ""));
    }

    // ------------------------------------------------------------------- JWT

    @Test
    @DisplayName("TC-U15: an issued token round-trips to the correct identity and role")
    void tokenRoundTrip() {
        JwtUtil jwt = new JwtUtil(SECRET, 8);
        String token = jwt.issue("u1", "Ally Mongala", "ally@mongalalaw.co.za", Role.LAWYER);

        JwtUtil.Session s = jwt.parse(token);

        assertNotNull(s, "a freshly issued token must parse");
        assertEquals("u1", s.userId());
        assertEquals("ally@mongalalaw.co.za", s.email());
        assertEquals(Role.LAWYER, s.role());
    }

    @Test
    @DisplayName("TC-U16: a token signed with a different secret is rejected")
    void tokenWithWrongSecretRejected() {
        JwtUtil issuer = new JwtUtil(SECRET, 8);
        JwtUtil attacker = new JwtUtil("a-completely-different-secret-key-value-here!!", 8);

        String token = issuer.issue("u1", "Ally", "ally@example.com", Role.LAWYER);

        assertNull(attacker.parse(token), "signature validation must reject a foreign token");
    }

    @Test
    @DisplayName("TC-U17: a tampered token is rejected")
    void tamperedTokenRejected() {
        JwtUtil jwt = new JwtUtil(SECRET, 8);
        String token = jwt.issue("u1", "Ally", "ally@example.com", Role.CLIENT);

        // flip the final character of the signature
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertNull(jwt.parse(tampered), "a modified token must not validate");
    }

    @Test
    @DisplayName("TC-U18: an expired token is rejected")
    void expiredTokenRejected() {
        JwtUtil expired = new JwtUtil(SECRET, 0);   // zero-hour TTL
        String token = expired.issue("u1", "Ally", "ally@example.com", Role.LAWYER);

        assertNull(expired.parse(token), "a token past its expiry must not validate");
    }

    @Test
    @DisplayName("TC-U19: a client token never carries case-write or admin scopes (FR-07)")
    void clientScopesAreRestricted() {
        Set<String> client = JwtUtil.scopesFor(Role.CLIENT);

        assertTrue(client.contains("read:client"), "a client may read their own portal");
        assertFalse(client.contains("write:case"), "a client must not be able to write cases");
        assertFalse(client.contains("admin:model"), "a client must not hold admin scope");
    }

    @Test
    @DisplayName("TC-U20: only the administrator role holds admin:model")
    void onlyAdminHoldsAdminScope() {
        assertTrue(JwtUtil.scopesFor(Role.ADMIN).contains("admin:model"));
        assertFalse(JwtUtil.scopesFor(Role.LAWYER).contains("admin:model"));
        assertFalse(JwtUtil.scopesFor(Role.CLIENT).contains("admin:model"));
    }

    @Test
    @DisplayName("TC-U21: Session.has() reflects the scope set carried by the token")
    void sessionScopeCheck() {
        JwtUtil jwt = new JwtUtil(SECRET, 8);
        JwtUtil.Session lawyer = jwt.parse(
                jwt.issue("u1", "Ally", "ally@example.com", Role.LAWYER));

        assertNotNull(lawyer);
        assertTrue(lawyer.has("write:case"));
        assertFalse(lawyer.has("admin:model"));
    }
}
