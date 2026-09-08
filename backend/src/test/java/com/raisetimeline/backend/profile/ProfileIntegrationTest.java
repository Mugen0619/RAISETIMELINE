package com.raisetimeline.backend.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.backend.auth.AuthResponse;
import com.raisetimeline.backend.auth.RegisterRequest;
import com.raisetimeline.backend.post.PostRequest;
import com.raisetimeline.backend.post.PostResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@SuppressWarnings({ "unchecked", "rawtypes" })
class ProfileIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	private String url(String path) {
		return "http://localhost:" + port + path;
	}

	private record Registered(String token, Long userId) {
	}

	private Registered register(String username) {
		RegisterRequest request = new RegisterRequest(username, username + "@example.com", "password123", username);
		ResponseEntity<AuthResponse> response = restTemplate.postForEntity(url("/api/auth/register"), request, AuthResponse.class);
		return new Registered(response.getBody().accessToken(), response.getBody().userId());
	}

	private <T> HttpEntity<T> authedBody(T body, String accessToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		return new HttpEntity<>(body, headers);
	}

	private HttpEntity<Void> authedNoBody(String accessToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		return new HttpEntity<>(headers);
	}

	@Test
	void getProfileReturnsCountsAndFollowedByMe() {
		Registered alice = register("palice");
		Registered bob = register("pbob");

		restTemplate.exchange(url("/api/users/" + bob.userId() + "/follow"), HttpMethod.POST, authedNoBody(alice.token()), Void.class);

		ResponseEntity<ProfileResponse> profile = restTemplate.exchange(
				url("/api/users/" + bob.userId()), HttpMethod.GET, authedNoBody(alice.token()), ProfileResponse.class);

		assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(profile.getBody().username()).isEqualTo("pbob");
		assertThat(profile.getBody().followerCount()).isEqualTo(1L);
		assertThat(profile.getBody().followedByMe()).isTrue();
	}

	@Test
	void getProfileReturns404ForUnknownUser() {
		Registered alice = register("pcarol");

		ResponseEntity<String> response = restTemplate.exchange(
				url("/api/users/999999"), HttpMethod.GET, authedNoBody(alice.token()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void updateProfileSucceedsForOwnerAndForbiddenForOtherUser() {
		Registered alice = register("pdave");
		Registered bob = register("perin");

		ResponseEntity<ProfileResponse> ownerUpdate = restTemplate.exchange(
				url("/api/users/" + alice.userId()), HttpMethod.PUT,
				authedBody(new ProfileUpdateRequest("Alice Updated", "new bio"), alice.token()), ProfileResponse.class);
		assertThat(ownerUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(ownerUpdate.getBody().displayName()).isEqualTo("Alice Updated");
		assertThat(ownerUpdate.getBody().bio()).isEqualTo("new bio");

		ResponseEntity<String> otherUpdate = restTemplate.exchange(
				url("/api/users/" + alice.userId()), HttpMethod.PUT,
				authedBody(new ProfileUpdateRequest("Hijacked", "bio"), bob.token()), String.class);
		assertThat(otherUpdate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		ResponseEntity<ProfileResponse> verify = restTemplate.exchange(
				url("/api/users/" + alice.userId()), HttpMethod.GET, authedNoBody(alice.token()), ProfileResponse.class);
		assertThat(verify.getBody().displayName()).isEqualTo("Alice Updated");
	}

	@Test
	void updateProfileRejectsBlankDisplayName() {
		Registered alice = register("pfrank");

		ResponseEntity<String> response = restTemplate.exchange(
				url("/api/users/" + alice.userId()), HttpMethod.PUT,
				authedBody(new ProfileUpdateRequest("", "bio"), alice.token()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void getUserPostsReturnsOnlyThatUsersPosts() {
		Registered alice = register("pgrace");
		Registered bob = register("pheidi");

		restTemplate.exchange(url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest("alice post"), alice.token()), PostResponse.class);
		restTemplate.exchange(url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest("bob post"), bob.token()), PostResponse.class);

		ResponseEntity<Map> response = restTemplate.exchange(
				url("/api/users/" + alice.userId() + "/posts"), HttpMethod.GET, authedNoBody(alice.token()), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
		assertThat(content).hasSize(1);
		assertThat(content.get(0).get("body")).isEqualTo("alice post");
	}

	@Test
	void getUserPostsReturns404ForUnknownUser() {
		Registered alice = register("pivan");

		ResponseEntity<String> response = restTemplate.exchange(
				url("/api/users/999999/posts"), HttpMethod.GET, authedNoBody(alice.token()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}
}
