package com.pnimac.comment.service;

import java.util.Date;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.pnimac.comment.model.Comment;
import com.pnimac.comment.repository.CommentRepository;

import jakarta.transaction.Transactional;

@Service
public class CommentService {

	private final CommentRepository commentRepository;
	private final Clock clock;

	public CommentService(CommentRepository commentRepository, Clock clock) {
		this.commentRepository = commentRepository;
		this.clock = clock;
	}
	
	@Transactional
	public Comment save(Comment comment) {
		comment.setCreatedAt(Date.from(clock.instant()));
		return commentRepository.saveAndFlush(comment);
	}
	
	public List<Comment> findAllByOrderByCreatedAtDesc(){
		return commentRepository.findAllByOrderByCreatedAtAsc();
	}
	
	public List<Comment> findByPostidByOrderByCreatedAtDesc(Long postId){
		return commentRepository.findByPostIdOrderByCreatedAtDesc(postId);
	}
	
	@Transactional
	public void deleteComment(Long commentId, String username) {
		Optional<Comment> comment = commentRepository.findById(commentId);
		if(comment.isPresent()) {
			if (!comment.get().getUsername().equals(username)) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can delete this comment");
			}
			commentRepository.deleteById(commentId);
		} else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found");
        }
	}
}
