package com.raisetimeline.backend.comment;

import com.raisetimeline.backend.user.User;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
public class PostCommentController {

	private final CommentService commentService;

	public PostCommentController(CommentService commentService) {
		this.commentService = commentService;
	}

	@PostMapping
	public ResponseEntity<CommentResponse> createComment(
			@PathVariable Long postId,
			@AuthenticationPrincipal User author,
			@Valid @RequestBody CommentRequest request) {
		CommentResponse response = commentService.createComment(postId, author, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping
	public PagedModel<CommentResponse> getComments(
			@PathVariable Long postId,
			@PageableDefault(size = 20, sort = { "createdAt", "id" }, direction = Sort.Direction.ASC) Pageable pageable) {
		return new PagedModel<>(commentService.getComments(postId, pageable));
	}
}
