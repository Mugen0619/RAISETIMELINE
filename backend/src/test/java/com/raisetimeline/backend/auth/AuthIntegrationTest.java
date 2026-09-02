package com.raisetimeline.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.backend.common.MeController;
import com.raisetimeline.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class AuthIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private UserRepository userRepository;

	private String url(String path) {
		return "http://localhost:" + port + path;
	}

	@Test
	void registerCreatesUserWithHashedPassword() {
		RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "password123", "Alice");

		ResponseEntity<AuthResponse> response = restTemplate.postForEntity(url("/api/auth/register"), request, AuthResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().accessToken()).isNotBlank();
		assertThat(response.getBody().refreshToken()).isNotBlank();

		var saved = userRepository.findByEmail("alice@example.com").orElseThrow();
		assertThat(saved.getPasswordHash()).isNotEqualTo("password123");
		assertThat(saved.getPasswordHash()).startsWith("$2");
	}

	@Test
	void registerRejectsDuplicateUsernameAndEmail() {
		RegisterRequest first = new RegisterRequest("bob", "bob@example.com", "password123", "Bob");
		restTemplate.postForEntity(url("/api/auth/register"), first, AuthResponse.class);

		RegisterRequest duplicateUsername = new RegisterRequest("bob", "other@example.com", "password123", "Bob2");
		ResponseEntity<String> res1 = restTemplate.postForEntity(url("/api/auth/register"), duplicateUsername, String.class);
		assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

		RegisterRequest duplicateEmail = new RegisterRequest("bob2", "bob@example.com", "password123", "Bob3");
		ResponseEntity<String> res2 = restTemplate.postForEntity(url("/api/auth/register"), duplicateEmail, String.class);
		assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void loginSucceedsWithCorrectCredentialsAndFailsWithWrongPassword() {
		RegisterRequest register = new RegisterRequest("carol", "carol@example.com", "password123", "Carol");
		restTemplate.postForEntity(url("/api/auth/register"), register, AuthResponse.class);

		LoginRequest correct = new LoginRequest("carol@example.com", "password123");
		ResponseEntity<AuthResponse> ok = restTemplate.postForEntity(url("/api/auth/login"), correct, AuthResponse.class);
		assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(ok.getBody()).isNotNull();
		assertThat(ok.getBody().accessToken()).isNotBlank();
		assertThat(ok.getBody().refreshToken()).isNotBlank();

		LoginRequest wrong = new LoginRequest("carol@example.com", "wrong-password");
		ResponseEntity<String> unauthorized = restTemplate.postForEntity(url("/api/auth/login"), wrong, String.class);
		assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void protectedEndpointRequiresValidJwt() {
		RegisterRequest register = new RegisterRequest("dave", "dave@example.com", "password123", "Dave");
		AuthResponse registered = restTemplate.postForEntity(url("/api/auth/register"), register, AuthResponse.class).getBody();

		ResponseEntity<String> noToken = restTemplate.getForEntity(url("/api/me"), String.class);
		assertThat(noToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		HttpHeaders invalidHeaders = new HttpHeaders();
		invalidHeaders.setBearerAuth("this-is-not-a-valid-jwt");
		ResponseEntity<String> invalidToken = restTemplate.exchange(
				url("/api/me"), org.springframework.http.HttpMethod.GET, new HttpEntity<>(invalidHeaders), String.class);
		assertThat(invalidToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		HttpHeaders validHeaders = new HttpHeaders();
		validHeaders.setBearerAuth(registered.accessToken());
		ResponseEntity<MeController.MeResponse> withToken = restTemplate.exchange(
				url("/api/me"), org.springframework.http.HttpMethod.GET, new HttpEntity<>(validHeaders), MeController.MeResponse.class);
		assertThat(withToken.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(withToken.getBody()).isNotNull();
		assertThat(withToken.getBody().username()).isEqualTo("dave");
	}

	@Test
	void refreshIssuesNewTokenPairAndRotatesOldRefreshToken() {
		RegisterRequest register = new RegisterRequest("erin", "erin@example.com", "password123", "Erin");
		AuthResponse registered = restTemplate.postForEntity(url("/api/auth/register"), register, AuthResponse.class).getBody();

		RefreshRequest refreshRequest = new RefreshRequest(registered.refreshToken());
		ResponseEntity<RefreshResponse> refreshed =
				restTemplate.postForEntity(url("/api/auth/refresh"), refreshRequest, RefreshResponse.class);

		assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(refreshed.getBody()).isNotNull();
		assertThat(refreshed.getBody().accessToken()).isNotBlank();
		assertThat(refreshed.getBody().refreshToken()).isNotBlank();
		assertThat(refreshed.getBody().refreshToken()).isNotEqualTo(registered.refreshToken());

		ResponseEntity<String> reuseOldToken =
				restTemplate.postForEntity(url("/api/auth/refresh"), refreshRequest, String.class);
		assertThat(reuseOldToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void refreshRejectsUnknownToken() {
		RefreshRequest refreshRequest = new RefreshRequest("this-refresh-token-does-not-exist");

		ResponseEntity<String> response = restTemplate.postForEntity(url("/api/auth/refresh"), refreshRequest, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}
}
