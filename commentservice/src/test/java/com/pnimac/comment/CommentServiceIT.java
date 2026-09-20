package com.pnimac.comment;

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

import com.pnimac.comment.model.Comment;
import com.pnimac.comment.repository.CommentRepository;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@Testcontainers
@ActiveProfiles("it")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CommentServiceIT {
    private static final String SECRET = "test-only-jwt-secret-with-at-least-thirty-two-characters";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0");

    @Autowired TestRestTemplate rest;
    @Autowired CommentRepository comments;

    @BeforeEach
    void clean() {
        comments.deleteAll();
    }

    @Test
    void createsWithAuthenticatedIdentityAndEnforcesOwnership() {
        ResponseEntity<Map> created = rest.exchange("/api/comment/addComment", HttpMethod.POST,
                entity(token("alice"), Map.of("postId", 7, "content", "Hello", "username", "victim")), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("username", "alice");
        long id = ((Number) created.getBody().get("id")).longValue();

        ResponseEntity<Map> forbidden = rest.exchange("/api/comment/deleteComment/" + id, HttpMethod.DELETE,
                entity(token("bob"), null), Map.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody()).containsEntry("code", "NOT_OWNER");
        assertThat(comments.existsById(id)).isTrue();

        assertThat(rest.exchange("/api/comment/deleteComment/" + id, HttpMethod.DELETE,
                entity(token("alice"), null), Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void validatesPayloadAndRejectsBadTokens() {
        ResponseEntity<Map> invalid = rest.exchange("/api/comment/addComment", HttpMethod.POST,
                entity(token("alice"), Map.of("postId", 0, "content", "")), Map.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody()).containsEntry("code", "VALIDATION_FAILED");

        ResponseEntity<Map> unauthorized = rest.exchange("/api/comment/getComments/7", HttpMethod.GET,
                entity("not-a-jwt", null), Map.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unauthorized.getBody()).containsEntry("code", "INVALID_TOKEN");

        ResponseEntity<Map> missingRoles = rest.exchange("/api/comment/getComments/7", HttpMethod.GET,
                entity(tokenWithoutRoles("alice"), null), Map.class);
        assertThat(missingRoles.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(missingRoles.getBody()).containsEntry("code", "INVALID_TOKEN");
    }

    @Test
    void returnsOnlyRequestedPostNewestFirstAndMissingDeleteIsNotFound() {
        comments.save(comment(7L, "old", new Date(1_000)));
        comments.save(comment(7L, "new", new Date(2_000)));
        comments.save(comment(8L, "other", new Date(3_000)));

        ResponseEntity<List> list = rest.exchange("/api/comment/getComments/7", HttpMethod.GET,
                entity(token("alice"), null), List.class);
        assertThat(list.getBody()).hasSize(2);
        assertThat(((Map<?, ?>) list.getBody().get(0)).get("content")).isEqualTo("new");

        ResponseEntity<Map> missing = rest.exchange("/api/comment/deleteComment/999999", HttpMethod.DELETE,
                entity(token("alice"), null), Map.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).containsEntry("code", "RESOURCE_NOT_FOUND");
    }

    private Comment comment(long postId, String content, Date createdAt) {
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setContent(content);
        comment.setUsername("alice");
        comment.setCreatedAt(createdAt);
        return comment;
    }

    private <T> HttpEntity<T> entity(String jwt, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        return new HttpEntity<>(body, headers);
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
