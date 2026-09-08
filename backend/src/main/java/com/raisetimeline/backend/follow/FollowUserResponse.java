package com.raisetimeline.backend.follow;

import com.raisetimeline.backend.user.User;

public record FollowUserResponse(
		Long userId,
		String username,
		String displayName,
		String avatarUrl,
		boolean followedByMe) {

	public static FollowUserResponse from(User user, boolean followedByMe) {
		return new FollowUserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getAvatarUrl(), followedByMe);
	}
}
