package com.pnimac.comment.service;

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

import com.pnimac.comment.model.Comment;
import com.pnimac.comment.repository.CommentRepository;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {
    @Mock CommentRepository repository;
    private CommentService service;
    private final Instant now = Instant.parse("2026-09-20T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = new CommentService(repository, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void addsTimestampBeforeSaving() {
        Comment comment = new Comment();
        when(repository.saveAndFlush(comment)).thenReturn(comment);
        assertThat(service.save(comment).getCreatedAt().toInstant()).isEqualTo(now);
    }

    @Test
    void delegatesOrderedPostLookup() {
        service.findByPostidByOrderByCreatedAtDesc(7L);
        verify(repository).findByPostIdOrderByCreatedAtDesc(7L);
    }

    @Test
    void ownerCanDelete() {
        when(repository.findById(1L)).thenReturn(Optional.of(ownedBy("alice")));
        service.deleteComment(1L, "alice");
        verify(repository).deleteById(1L);
    }

    @Test
    void nonOwnerCannotDelete() {
        when(repository.findById(1L)).thenReturn(Optional.of(ownedBy("alice")));
        assertThatThrownBy(() -> service.deleteComment(1L, "bob"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403 FORBIDDEN");
        verify(repository, never()).deleteById(1L);
    }

    @Test
    void missingCommentReturnsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteComment(99L, "alice"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404 NOT_FOUND");
    }

    private Comment ownedBy(String username) {
        Comment comment = new Comment();
        comment.setUsername(username);
        return comment;
    }
}
