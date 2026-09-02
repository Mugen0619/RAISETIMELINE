package com.raisetimeline.backend.auth;

import com.raisetimeline.backend.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * リフレッシュトークンはランダムな不透明トークンとして発行し、DBにはSHA-256ハッシュのみを保存する
 * (漏洩時にDB内容から生トークンを復元できないようにするため)。使用の都度、旧トークンを削除し
 * 新トークンを発行するローテーション方式を取ることで、盗まれたトークンの使い回しを防ぐ。
 */
@Service
public class RefreshTokenService {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final RefreshTokenRepository refreshTokenRepository;
	private final long refreshExpirationMs;

	public RefreshTokenService(
			RefreshTokenRepository refreshTokenRepository,
			@Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.refreshExpirationMs = refreshExpirationMs;
	}

	@Transactional
	public String issue(User user) {
		byte[] randomBytes = new byte[32];
		SECURE_RANDOM.nextBytes(randomBytes);
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

		RefreshToken refreshToken = new RefreshToken(user, hash(rawToken), Instant.now().plusMillis(refreshExpirationMs));
		refreshTokenRepository.save(refreshToken);

		return rawToken;
	}

	@Transactional
	public User rotate(String rawToken) {
		RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawToken))
				.orElseThrow(() -> new InvalidRefreshTokenException("invalid refresh token"));

		refreshTokenRepository.delete(stored);

		if (stored.isExpired()) {
			throw new InvalidRefreshTokenException("refresh token has expired");
		}

		return stored.getUser();
	}

	private String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 algorithm not available", e);
		}
	}
}
