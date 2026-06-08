package com.accesspath.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JwtUtil.
 * Covers token generation, claim extraction, expiry validation,
 * and rejection of malformed/expired tokens.
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    // 64-char secret → 512 bits → valid for HS256/HS512
    private static final String SECRET =
            "test-secret-key-that-is-long-enough-for-hs256-algorithm-padding";
    private static final long EXPIRATION_MS = 3_600_000L; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", EXPIRATION_MS);
    }

    // ── generateToken / extract claims ────────────────────────────────────────

    @Test
    @DisplayName("generateToken: extracted email matches input")
    void generateToken_extractEmail_matchesInput() {
        UUID userId = UUID.randomUUID();
        String token = jwtUtil.generateToken(userId, "user@test.com", "REPORTER");

        assertThat(jwtUtil.extractEmail(token)).isEqualTo("user@test.com");
    }

    @Test
    @DisplayName("generateToken: extracted userId matches input")
    void generateToken_extractUserId_matchesInput() {
        UUID userId = UUID.randomUUID();
        String token = jwtUtil.generateToken(userId, "user@test.com", "REPORTER");

        assertThat(jwtUtil.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    @DisplayName("generateToken: extracted role matches input")
    void generateToken_extractRole_matchesInput() {
        UUID userId = UUID.randomUUID();
        String token = jwtUtil.generateToken(userId, "user@test.com", "MODERATOR");

        assertThat(jwtUtil.extractRole(token)).isEqualTo("MODERATOR");
    }

    @Test
    @DisplayName("generateToken: different users produce different tokens")
    void generateToken_differentUsers_produceDifferentTokens() {
        String t1 = jwtUtil.generateToken(UUID.randomUUID(), "a@test.com", "REPORTER");
        String t2 = jwtUtil.generateToken(UUID.randomUUID(), "b@test.com", "REPORTER");

        assertThat(t1).isNotEqualTo(t2);
    }

    // ── isTokenValid ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("isTokenValid: fresh token → true")
    void isTokenValid_freshToken_returnsTrue() {
        String token = jwtUtil.generateToken(UUID.randomUUID(), "user@test.com", "REPORTER");

        assertThat(jwtUtil.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid: expired token → false")
    void isTokenValid_expiredToken_returnsFalse() {
        // Generate a token that expired 1ms ago
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", -1L);
        String expiredToken = jwtUtil.generateToken(
                UUID.randomUUID(), "user@test.com", "REPORTER");

        // Restore normal expiration for the check
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", EXPIRATION_MS);

        assertThat(jwtUtil.isTokenValid(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid: completely malformed string → false")
    void isTokenValid_malformedToken_returnsFalse() {
        assertThat(jwtUtil.isTokenValid("not.a.jwt.token")).isFalse();
    }

    @Test
    @DisplayName("isTokenValid: empty string → false")
    void isTokenValid_emptyString_returnsFalse() {
        assertThat(jwtUtil.isTokenValid("")).isFalse();
    }

    @Test
    @DisplayName("isTokenValid: token signed with different secret → false")
    void isTokenValid_wrongSecret_returnsFalse() {
        // Sign with different secret
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "completely-different-secret-key-that-is-long-enough-12345");
        String foreignToken = jwtUtil.generateToken(
                UUID.randomUUID(), "user@test.com", "REPORTER");

        // Restore original secret
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);

        assertThat(jwtUtil.isTokenValid(foreignToken)).isFalse();
    }
}
