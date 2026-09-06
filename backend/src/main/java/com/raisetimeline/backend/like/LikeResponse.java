package com.raisetimeline.backend.like;

public record LikeResponse(Long postId, boolean liked, long likeCount) {
}
