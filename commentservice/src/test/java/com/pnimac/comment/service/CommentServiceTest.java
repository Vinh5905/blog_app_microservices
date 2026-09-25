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

    /**
     * Verifies that the service assigns the injected clock time before flushing a new
     * comment. This keeps creation timestamps deterministic and avoids null or
     * machine-time-dependent values.
     */
    @Test
    void addsTimestampBeforeSaving() {
        Comment comment = new Comment();
        when(repository.saveAndFlush(comment)).thenReturn(comment);
        assertThat(service.save(comment).getCreatedAt().toInstant()).isEqualTo(now);
    }

    /**
     * Verifies that a lookup for one post is delegated to the repository method that
     * filters by post ID and sorts newest first. This prevents comments from different
     * posts being mixed or returned in an unexpected order.
     */
    @Test
    void delegatesOrderedPostLookup() {
        service.findByPostidByOrderByCreatedAtDesc(7L);
        verify(repository).findByPostIdOrderByCreatedAtDesc(7L);
    }

    /**
     * Verifies that the authenticated owner may delete their own comment. This protects
     * the expected success path of the ownership rule.
     */
    @Test
    void ownerCanDelete() {
        when(repository.findById(1L)).thenReturn(Optional.of(ownedBy("alice")));
        service.deleteComment(1L, "alice");
        verify(repository).deleteById(1L);
    }

    /**
     * Verifies that a different authenticated user receives 403 and that no delete is
     * sent to the repository. This prevents cross-user deletion and partial side effects.
     */
    @Test
    void nonOwnerCannotDelete() {
        when(repository.findById(1L)).thenReturn(Optional.of(ownedBy("alice")));
        assertThatThrownBy(() -> service.deleteComment(1L, "bob"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403 FORBIDDEN");
        verify(repository, never()).deleteById(1L);
    }

    /**
     * Verifies that deleting an unknown comment returns 404. This distinguishes a
     * missing resource from an authorization failure or an unexpected server error.
     */
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
