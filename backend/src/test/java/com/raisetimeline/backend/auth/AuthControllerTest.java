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
	void registerReturns201WithToken() throws Exception {
		RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "password123", "Alice");
		when(authService.register(any(RegisterRequest.class))).thenReturn(new AuthResponse("jwt-token", 1L, "alice"));

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token", is("jwt-token")))
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
	void loginReturns200WithToken() throws Exception {
		LoginRequest request = new LoginRequest("alice@example.com", "password123");
		when(authService.login(any(LoginRequest.class))).thenReturn(new AuthResponse("jwt-token", 1L, "alice"));

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token", is("jwt-token")));
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
}
