package com.raisetimeline.backend.comment;

import java.time.Instant;

public record CommentResponse(
		Long id,
		Long postId,
		Long userId,
		String username,
		String displayName,
		String body,
		Instant createdAt) {

	public static CommentResponse from(Comment comment) {
		return new CommentResponse(
				comment.getId(),
				comment.getPost().getId(),
				comment.getUser().getId(),
				comment.getUser().getUsername(),
				comment.getUser().getDisplayName(),
				comment.getBody(),
				comment.getCreatedAt());
	}
}
