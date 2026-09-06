package com.raisetimeline.backend.post;

import java.time.Instant;

public record PostResponse(
		Long id,
		Long userId,
		String username,
		String displayName,
		String body,
		Instant createdAt,
		Instant updatedAt) {

	public static PostResponse from(Post post) {
		return new PostResponse(
				post.getId(),
				post.getUser().getId(),
				post.getUser().getUsername(),
				post.getUser().getDisplayName(),
				post.getBody(),
				post.getCreatedAt(),
				post.getUpdatedAt());
	}
}
