package com.raisetimeline.backend.user;

public record UserSummaryResponse(
		Long userId,
		String username,
		String displayName,
		String avatarUrl) {

	public static UserSummaryResponse from(User user) {
		return new UserSummaryResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getAvatarUrl());
	}
}
