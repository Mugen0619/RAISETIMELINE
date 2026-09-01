package com.raisetimeline.backend.auth;

public record AuthResponse(String token, Long userId, String username) {
}
