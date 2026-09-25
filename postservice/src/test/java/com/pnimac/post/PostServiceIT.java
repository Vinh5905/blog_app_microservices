package com.pnimac.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.List;
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
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.pnimac.post.repository.PostRepository;
import com.pnimac.post.model.Post;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@Testcontainers
@ActiveProfiles("it")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PostServiceIT {
    private static final String SECRET = "test-only-jwt-secret-with-at-least-thirty-two-characters";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0");

    @Autowired TestRestTemplate rest;
    @Autowired PostRepository posts;

    @BeforeEach
    void clean() {
        posts.deleteAll();
    }

    /**
     * Verifies the complete ownership contract against MySQL 8.4: create uses the JWT
     * subject instead of a client-supplied username, a different user receives
     * 403/NOT_OWNER without data loss, and the real owner can delete with 204.
     */
    @Test
    void createsWithAuthenticatedIdentityAndEnforcesOwnership() {
        ResponseEntity<Map> created = rest.exchange("/api/post/createPost", HttpMethod.POST,
                entity(token("alice"), Map.of("title", "First", "body", "Body", "username", "victim")), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("username", "alice");
        assertThat(created.getBody().get("createdAt")).isNotNull();
        long id = ((Number) created.getBody().get("id")).longValue();

        ResponseEntity<Map> forbidden = rest.exchange("/api/post/deletePost/" + id, HttpMethod.DELETE,
                entity(token("bob"), null), Map.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody()).containsEntry("code", "NOT_OWNER");
        assertThat(posts.existsById(id)).isTrue();

        ResponseEntity<Void> deleted = rest.exchange("/api/post/deletePost/" + id, HttpMethod.DELETE,
                entity(token("alice"), null), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    /**
     * Verifies that invalid post fields return 400/VALIDATION_FAILED and that both a
     * malformed JWT and a signed JWT missing roles return 401/INVALID_TOKEN. This
     * protects the validation and authentication boundaries from incomplete input.
     */
    @Test
    void validatesPayloadAndRejectsBadTokens() {
        ResponseEntity<Map> invalid = rest.exchange("/api/post/createPost", HttpMethod.POST,
                entity(token("alice"), Map.of("title", "", "body", "")), Map.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody()).containsEntry("code", "VALIDATION_FAILED");

        ResponseEntity<Map> unauthorized = rest.exchange("/api/post/getAllPosts", HttpMethod.GET,
                entity("not-a-jwt", null), Map.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unauthorized.getBody()).containsEntry("code", "INVALID_TOKEN");

        ResponseEntity<Map> missingRoles = rest.exchange("/api/post/getAllPosts", HttpMethod.GET,
                entity(tokenWithoutRoles("alice"), null), Map.class);
        assertThat(missingRoles.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(missingRoles.getBody()).containsEntry("code", "INVALID_TOKEN");
    }

    /**
     * Verifies that the feed returns newest posts first and that deleting an unknown ID
     * returns 404/RESOURCE_NOT_FOUND. This protects both the ordering contract used by
     * the UI and the stable HTTP error contract for missing data.
     */
    @Test
    void returnsNewestPostsFirstAndMissingDeleteIsNotFound() {
        posts.save(post("First", new Date(1_000)));
        posts.save(post("Second", new Date(2_000)));

        ResponseEntity<List> list = rest.exchange("/api/post/getAllPosts", HttpMethod.GET,
                entity(token("alice"), null), List.class);
        assertThat(((Map<?, ?>) list.getBody().get(0)).get("title")).isEqualTo("Second");

        ResponseEntity<Map> missing = rest.exchange("/api/post/deletePost/999999", HttpMethod.DELETE,
                entity(token("alice"), null), Map.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).containsEntry("code", "RESOURCE_NOT_FOUND");
    }

    private <T> HttpEntity<T> entity(String jwt, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        return new HttpEntity<>(body, headers);
    }

    private Post post(String title, Date createdAt) {
        Post post = new Post();
        post.setTitle(title);
        post.setContent("Body");
        post.setUsername("alice");
        post.setCreatedAt(createdAt);
        return post;
    }

    private String token(String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder().setSubject(username).claim("roles", List.of("ROLE_USER"))
                .setIssuedAt(new Date(now)).setExpiration(new Date(now + 3_600_000))
                .signWith(SignatureAlgorithm.HS256, SECRET).compact();
    }

    private String tokenWithoutRoles(String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder().setSubject(username)
                .setIssuedAt(new Date(now)).setExpiration(new Date(now + 3_600_000))
                .signWith(SignatureAlgorithm.HS256, SECRET).compact();
    }
}
