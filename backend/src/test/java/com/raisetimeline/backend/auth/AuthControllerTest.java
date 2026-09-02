package com.raisetimeline.backend.auth;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.backend.security.JwtService;
import com.raisetimeline.backend.user.UserRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * AuthControllerのWeb層テスト。AuthServiceはMockitoでモックし、
 * HTTPステータス・例外→レスポンスのマッピング(GlobalExceptionHandler)のみを検証する。
 * JWT認可の挙動はMeControllerSecurityTestで別途検証する。
 * addFilters=falseでセキュリティフィルタは無効化するが、JwtAuthenticationFilterが
 * @WebMvcTestのFilter自動検出でBean化されるため、その依存関係はモックしておく必要がある。
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private UserRepository userRepository;

	@Test
	void registerReturns201WithTokens() throws Exception {
		RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "password123", "Alice");
		when(authService.register(any(RegisterRequest.class)))
				.thenReturn(new AuthResponse("access-token", "refresh-token", 1L, "alice", "Alice"));

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accessToken", is("access-token")))
				.andExpect(jsonPath("$.refreshToken", is("refresh-token")))
				.andExpect(jsonPath("$.username", is("alice")));
	}

	@Test
	void registerReturns409WhenDuplicate() throws Exception {
		RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "password123", "Alice");
		when(authService.register(any(RegisterRequest.class)))
				.thenThrow(new DuplicateUserException("username is already taken"));

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isConflict());
	}

	@Test
	void registerReturns400ForInvalidEmail() throws Exception {
		RegisterRequest request = new RegisterRequest("alice", "not-an-email", "password123", "Alice");

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void loginReturns200WithTokens() throws Exception {
		LoginRequest request = new LoginRequest("alice@example.com", "password123");
		when(authService.login(any(LoginRequest.class)))
				.thenReturn(new AuthResponse("access-token", "refresh-token", 1L, "alice", "Alice"));

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken", is("access-token")))
				.andExpect(jsonPath("$.refreshToken", is("refresh-token")));
	}

	@Test
	void loginReturns401ForInvalidCredentials() throws Exception {
		LoginRequest request = new LoginRequest("alice@example.com", "wrong-password");
		when(authService.login(any(LoginRequest.class)))
				.thenThrow(new InvalidCredentialsException("invalid email or password"));

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshReturns200WithNewTokens() throws Exception {
		RefreshRequest request = new RefreshRequest("old-refresh-token");
		when(authService.refresh(any(RefreshRequest.class)))
				.thenReturn(new RefreshResponse("new-access-token", "new-refresh-token"));

		mockMvc.perform(post("/api/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken", is("new-access-token")))
				.andExpect(jsonPath("$.refreshToken", is("new-refresh-token")));
	}

	@Test
	void refreshReturns401ForInvalidToken() throws Exception {
		RefreshRequest request = new RefreshRequest("bogus-token");
		when(authService.refresh(any(RefreshRequest.class)))
				.thenThrow(new InvalidRefreshTokenException("invalid refresh token"));

		mockMvc.perform(post("/api/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshReturns400ForBlankToken() throws Exception {
		RefreshRequest request = new RefreshRequest("");

		mockMvc.perform(post("/api/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest());
	}
}
