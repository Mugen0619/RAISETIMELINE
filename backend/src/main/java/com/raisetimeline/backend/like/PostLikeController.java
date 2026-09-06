package com.raisetimeline.backend.like;

import com.raisetimeline.backend.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts/{postId}/likes")
public class PostLikeController {

	private final LikeService likeService;

	public PostLikeController(LikeService likeService) {
		this.likeService = likeService;
	}

	@PostMapping
	public ResponseEntity<LikeResponse> toggleLike(
			@PathVariable Long postId,
			@AuthenticationPrincipal User currentUser) {
		LikeResponse response = likeService.toggleLike(postId, currentUser);
		return ResponseEntity.ok(response);
	}
}
