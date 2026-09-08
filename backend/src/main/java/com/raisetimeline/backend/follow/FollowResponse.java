package com.raisetimeline.backend.follow;

public record FollowResponse(Long userId, boolean following, long followerCount) {
}
