package com.pnimac.post.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pnimac.post.dto.PostDTO;
import com.pnimac.post.model.Post;
import com.pnimac.post.request.PostRequest;
import com.pnimac.post.service.PostService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/post")
public class PostController {

	@Autowired
    private PostService postService;
	
	public PostController(PostService postService) {
		this.postService = postService;
	}
	
    @GetMapping("/getAllPosts")
    public List<PostDTO> getAllPosts() {
    	List<Post> posts = postService.getAllPosts();
    	List<PostDTO> postDTOs = posts.stream()
                .map(PostDTO::fromEntity) 
                .collect(Collectors.toList());
        return postDTOs;
    }

    @PostMapping("/createPost")
    public ResponseEntity<PostDTO> createPost(@Valid @RequestBody PostRequest postRequest, Authentication authentication) {
    	Post post = new Post();
    	post.setTitle(postRequest.getTitle());
    	post.setContent(postRequest.getBody());
	post.setUsername(authentication.getName());
        PostDTO createdPost = PostDTO.fromEntity(postService.addPost(post));
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPost);
    }

    @DeleteMapping("deletePost/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable Long postId, Authentication authentication) {
        postService.deletePost(postId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
