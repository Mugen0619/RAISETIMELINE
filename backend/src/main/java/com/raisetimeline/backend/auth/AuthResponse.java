package com.raisetimeline.backend.auth;

public record AuthResponse(String accessToken, String refreshToken, Long userId, String username, String displayName) {
}
