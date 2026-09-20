package com.pnimac.comment.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CommentRequest {

	@NotBlank
	@Size(max = 10000)
	private String content;

	@NotNull
	@Positive
	private Long postId;
	private String username;
	
	public String getContent() {
		return content;
	}
	public void setContent(String content) {
		this.content = content;
	}
	public Long getPostId() {
		return postId;
	}
	public void setPostId(Long postId) {
		this.postId = postId;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	
	public CommentRequest() {}
	
	public CommentRequest(String content, Long postId, String username) {
		super();
		this.content = content;
		this.postId = postId;
		this.username = username;
	}
	
	
}
