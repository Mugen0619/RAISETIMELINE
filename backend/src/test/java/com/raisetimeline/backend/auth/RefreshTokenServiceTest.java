package com.raisetimeline.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raisetimeline.backend.user.User;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

	private static final long REFRESH_EXPIRATION_MS = 1_209_600_000L;

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	private RefreshTokenService refreshTokenService;

	@BeforeEach
	void setUp() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, REFRESH_EXPIRATION_MS);
	}

	@Test
	void issueStoresHashedTokenNotRawToken() {
		User user = new User("alice", "alice@example.com", "hashed", "Alice");

		String rawToken = refreshTokenService.issue(user);

		ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository).save(captor.capture());
		RefreshToken saved = captor.getValue();

		assertThat(rawToken).isNotBlank();
		assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
		assertThat(saved.getUser()).isSameAs(user);
		assertThat(saved.getExpiresAt()).isAfter(Instant.now());
	}

	@Test
	void rotateReturnsUserAndDeletesConsumedTokenForValidToken() {
		User user = new User("bob", "bob@example.com", "hashed", "Bob");
		RefreshToken stored = new RefreshToken(user, "irrelevant-in-test", Instant.now().plusSeconds(60));
		when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));

		User result = refreshTokenService.rotate("some-raw-token");

		assertThat(result).isSameAs(user);
		verify(refreshTokenRepository).delete(stored);
	}

	@Test
	void rotateThrowsForUnknownToken() {
		when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> refreshTokenService.rotate("unknown-token"))
				.isInstanceOf(InvalidRefreshTokenException.class);

		verify(refreshTokenRepository, never()).delete(any());
	}

	@Test
	void rotateThrowsAndDeletesForExpiredToken() {
		User user = new User("carol", "carol@example.com", "hashed", "Carol");
		RefreshToken expired = new RefreshToken(user, "irrelevant-in-test", Instant.now().minusSeconds(1));
		when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

		assertThatThrownBy(() -> refreshTokenService.rotate("expired-raw-token"))
				.isInstanceOf(InvalidRefreshTokenException.class);

		verify(refreshTokenRepository).delete(expired);
	}

	@Test
	void issueThenRotateRoundTripsWithMatchingHash() {
		User user = new User("dave", "dave@example.com", "hashed", "Dave");
		ReflectionTestUtils.setField(user, "id", 1L);
		ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);

		String rawToken = refreshTokenService.issue(user);
		verify(refreshTokenRepository).save(captor.capture());
		RefreshToken saved = captor.getValue();
		when(refreshTokenRepository.findByTokenHash(saved.getTokenHash())).thenReturn(Optional.of(saved));

		User result = refreshTokenService.rotate(rawToken);

		assertThat(result).isSameAs(user);
	}
}
