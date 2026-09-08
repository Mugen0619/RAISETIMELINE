package com.raisetimeline.backend.profile;

import com.raisetimeline.backend.user.User;
import java.time.Instant;

public record ProfileResponse(
		Long userId,
		String username,
		String displayName,
		String bio,
		String avatarUrl,
		long followerCount,
		long followingCount,
		boolean followedByMe,
		Instant createdAt) {

	public static ProfileResponse from(User user, long followerCount, long followingCount, boolean followedByMe) {
		return new ProfileResponse(
				user.getId(),
				user.getUsername(),
				user.getDisplayName(),
				user.getBio(),
				user.getAvatarUrl(),
				followerCount,
				followingCount,
				followedByMe,
				user.getCreatedAt());
	}
}
