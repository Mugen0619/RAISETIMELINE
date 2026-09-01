package com.raisetimeline.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raisetimeline.backend.security.JwtService;
import com.raisetimeline.backend.user.User;
import com.raisetimeline.backend.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * AuthServiceの単体テスト。PasswordEncoderは実際のBCrypt実装を使い、
 * パスワードが平文のまま保存されないことを実際のハッシュ照合で検証する。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private JwtService jwtService;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	private AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(userRepository, passwordEncoder, jwtService);
	}

	@Test
	void registerCreatesUserWithBcryptHashedPassword() {
		RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "password123", "Alice");
		when(userRepository.existsByUsername("alice")).thenReturn(false);
		when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(jwtService.generateToken(any(), eq("alice"))).thenReturn("fake-jwt-token");

		AuthResponse response = authService.register(request);

		assertThat(response.token()).isEqualTo("fake-jwt-token");
		assertThat(response.username()).isEqualTo("alice");

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		User saved = captor.getValue();

		assertThat(saved.getPasswordHash()).isNotEqualTo("password123");
		assertThat(saved.getPasswordHash()).startsWith("$2");
		assertThat(passwordEncoder.matches("password123", saved.getPasswordHash())).isTrue();
	}

	@Test
	void registerUsesUsernameAsDisplayNameWhenNotProvided() {
		RegisterRequest request = new RegisterRequest("noname", "noname@example.com", "password123", null);
		when(userRepository.existsByUsername("noname")).thenReturn(false);
		when(userRepository.existsByEmail("noname@example.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(jwtService.generateToken(any(), eq("noname"))).thenReturn("fake-jwt-token");

		authService.register(request);

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		assertThat(captor.getValue().getDisplayName()).isEqualTo("noname");
	}

	@Test
	void registerThrowsWhenUsernameAlreadyExists() {
		when(userRepository.existsByUsername("bob")).thenReturn(true);
		RegisterRequest request = new RegisterRequest("bob", "bob@example.com", "password123", null);

		assertThatThrownBy(() -> authService.register(request)).isInstanceOf(DuplicateUserException.class);

		verify(userRepository, never()).save(any());
	}

	@Test
	void registerThrowsWhenEmailAlreadyRegistered() {
		when(userRepository.existsByUsername("carol")).thenReturn(false);
		when(userRepository.existsByEmail("carol@example.com")).thenReturn(true);
		RegisterRequest request = new RegisterRequest("carol", "carol@example.com", "password123", null);

		assertThatThrownBy(() -> authService.register(request)).isInstanceOf(DuplicateUserException.class);

		verify(userRepository, never()).save(any());
	}

	@Test
	void loginReturnsJwtForCorrectCredentials() {
		User user = new User("dave", "dave@example.com", passwordEncoder.encode("password123"), "Dave");
		ReflectionTestUtils.setField(user, "id", 42L);
		when(userRepository.findByEmail("dave@example.com")).thenReturn(Optional.of(user));
		when(jwtService.generateToken(42L, "dave")).thenReturn("fake-jwt-token");

		AuthResponse response = authService.login(new LoginRequest("dave@example.com", "password123"));

		assertThat(response.token()).isEqualTo("fake-jwt-token");
		assertThat(response.userId()).isEqualTo(42L);
		assertThat(response.username()).isEqualTo("dave");
	}

	@Test
	void loginThrowsInvalidCredentialsForWrongPassword() {
		User user = new User("erin", "erin@example.com", passwordEncoder.encode("password123"), "Erin");
		when(userRepository.findByEmail("erin@example.com")).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> authService.login(new LoginRequest("erin@example.com", "wrong-password")))
				.isInstanceOf(InvalidCredentialsException.class);

		verify(jwtService, never()).generateToken(anyLong(), any());
	}

	@Test
	void loginThrowsInvalidCredentialsForUnknownEmail() {
		when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "password123")))
				.isInstanceOf(InvalidCredentialsException.class);

		verify(jwtService, never()).generateToken(anyLong(), any());
	}
}
