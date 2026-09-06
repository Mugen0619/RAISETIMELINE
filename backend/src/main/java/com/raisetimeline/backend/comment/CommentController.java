package com.raisetimeline.backend.comment;

import com.raisetimeline.backend.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

	private final CommentService commentService;

	public CommentController(CommentService commentService) {
		this.commentService = commentService;
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteComment(
			@PathVariable Long id,
			@AuthenticationPrincipal User currentUser) {
		commentService.deleteComment(id, currentUser);
		return ResponseEntity.noContent().build();
	}
}
