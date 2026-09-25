package com.pnimac.auth.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

class JwtUtilTest {
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-only-jwt-secret-with-at-least-thirty-two-characters");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3600L);
    }

    /**
     * Verifies that a newly issued token preserves the authenticated username and
     * roles, has a future expiry, and validates for the same user. This protects the
     * JWT contract consumed by the gateway-facing services.
     */
    @Test
    void generatesExpectedIdentityAndRoles() {
        User user = new User("alice", "secret", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        String token = jwtUtil.generateToken(user);

        assertThat(jwtUtil.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtUtil.extractAllClaims(token).get("roles", List.class)).containsExactly("ROLE_USER");
        assertThat(jwtUtil.extractExpiration(token)).isAfter(new java.util.Date());
        assertThat(jwtUtil.validateToken(token, user)).isTrue();
    }

    /**
     * Verifies that an already expired token is rejected with ExpiredJwtException.
     * This prevents old credentials from remaining usable after their lifetime.
     */
    @Test
    void rejectsExpiredToken() {
        ReflectionTestUtils.setField(jwtUtil, "expiration", -1L);
        String token = jwtUtil.generateToken(new User("alice", "secret", List.of()));
        assertThatThrownBy(() -> jwtUtil.validateToken(token, new User("alice", "secret", List.of())))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    /**
     * Verifies that input which is not a JWT cannot be parsed as an identity. This
     * protects authentication code from accepting malformed bearer credentials.
     */
    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> jwtUtil.extractUsername("not-a-jwt")).isInstanceOf(RuntimeException.class);
    }
}
