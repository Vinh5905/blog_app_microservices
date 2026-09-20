package com.pnimac.post.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.pnimac.post.model.Post;
import com.pnimac.post.repository.PostRepository;

import jakarta.transaction.Transactional;

@Service
public class PostService {
	
	@Autowired
    private PostRepository postRepository;

	public List<Post> getAllPosts() {
        return postRepository.findAllByOrderByCreatedAtDesc();
    }

	@Transactional
    public Post addPost(Post post) {
        return postRepository.saveAndFlush(post);
    }

	@Transactional
    public void deletePost(Long postId, String username) {
        Optional<Post> post = postRepository.findById(postId);
        if (post.isPresent()) {
            if (!post.get().getUsername().equals(username)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can delete this post");
            }
            postRepository.deleteById(postId);
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }
    }
}
