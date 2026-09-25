package com.pnimac.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.pnimac.auth.model.repository.UserRepository;

@Testcontainers
@ActiveProfiles("it")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserAuthServiceIT {
    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0");

    @Autowired TestRestTemplate rest;
    @Autowired UserRepository users;

    @BeforeEach
    void cleanUsers() {
        rest.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        users.deleteAll();
    }

    /**
     * Verifies the complete signup-login-current-user HTTP flow against MySQL 8.4:
     * signup returns 201 without exposing the password, BCrypt is persisted, login
     * issues a token, and that token resolves the correct current user. This protects
     * the primary authentication journey and response-data boundary.
     */
    @Test
    void signupLoginAndReadCurrentUser() {
        ResponseEntity<Map> signup = rest.postForEntity("/api/auth/signup",
                Map.of("username", "alice", "email", "alice@example.test", "password", "Password123!"), Map.class);
        assertThat(signup.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(signup.getBody()).containsEntry("username", "alice").doesNotContainKey("password");
        assertThat(users.findByUsername("alice").orElseThrow().getPassword()).startsWith("$2");

        ResponseEntity<Map> login = rest.postForEntity("/api/auth/login",
                Map.of("username", "alice", "password", "Password123!"), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = (String) login.getBody().get("token");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map> current = rest.exchange("/api/auth/user", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(current.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(current.getBody()).containsEntry("username", "alice").doesNotContainKey("password");
    }

    /**
     * Verifies production error contracts for two business failures: duplicate email
     * returns 409/DUPLICATE_EMAIL and a wrong password returns 401/BAD_CREDENTIALS.
     * This keeps frontend behavior stable and prevents expected failures becoming 500s.
     */
    @Test
    void duplicateEmailAndBadCredentialsUseProductionStatusCodes() {
        Map<String, String> body = Map.of("username", "alice", "email", "alice@example.test",
                "password", "Password123!");
        assertThat(rest.postForEntity("/api/auth/signup", body, Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> duplicate = rest.postForEntity("/api/auth/signup",
                Map.of("username", "alice2", "email", "alice@example.test", "password", "Password123!"), Map.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).containsEntry("code", "DUPLICATE_EMAIL");

        ResponseEntity<Map> badLogin = rest.postForEntity("/api/auth/login",
                Map.of("username", "alice", "password", "WrongPassword!"), Map.class);
        assertThat(badLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(badLogin.getBody()).containsEntry("code", "BAD_CREDENTIALS");
    }

    /**
     * Verifies that invalid signup fields return 400/VALIDATION_FAILED and a malformed
     * bearer token returns 401/INVALID_TOKEN. This protects both request validation and
     * the HTTP security boundary from leaking malformed input into application logic.
     */
    @Test
    void validatesPayloadAndRejectsInvalidJwt() {
        ResponseEntity<Map> invalid = rest.postForEntity("/api/auth/signup",
                Map.of("username", "a", "email", "bad", "password", "short"), Map.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody()).containsEntry("code", "VALIDATION_FAILED");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("not-a-jwt");
        ResponseEntity<Map> unauthorized = rest.exchange("/api/auth/user", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unauthorized.getBody()).containsEntry("code", "INVALID_TOKEN");
    }
}
