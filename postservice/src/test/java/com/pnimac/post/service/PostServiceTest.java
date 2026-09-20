package com.pnimac.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.pnimac.post.model.Post;
import com.pnimac.post.repository.PostRepository;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {
    @Mock PostRepository repository;
    private PostService service;
    private final Instant now = Instant.parse("2026-09-20T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = new PostService(repository, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void addsTimestampBeforeSaving() {
        Post post = new Post();
        when(repository.saveAndFlush(post)).thenReturn(post);
        assertThat(service.addPost(post).getCreatedAt().toInstant()).isEqualTo(now);
        verify(repository).saveAndFlush(post);
    }

    @Test
    void ownerCanDelete() {
        Post post = postOwnedBy("alice");
        when(repository.findById(1L)).thenReturn(Optional.of(post));
        service.deletePost(1L, "alice");
        verify(repository).deleteById(1L);
    }

    @Test
    void nonOwnerCannotDelete() {
        when(repository.findById(1L)).thenReturn(Optional.of(postOwnedBy("alice")));
        assertThatThrownBy(() -> service.deletePost(1L, "bob"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
        verify(repository, never()).deleteById(1L);
    }

    @Test
    void missingPostReturnsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deletePost(99L, "alice"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    private Post postOwnedBy(String username) {
        Post post = new Post();
        post.setUsername(username);
        return post;
    }
}
