package com.raisetimeline.backend.follow;

import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.backend.auth.AuthResponse;
import com.raisetimeline.backend.auth.RegisterRequest;
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
class FollowIntegrationTest {

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

	private HttpEntity<Void> authed(String accessToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		return new HttpEntity<>(headers);
	}

	@Test
	void followingTwiceTogglesOnThenOff() {
		Registered alice = register("falice");
		Registered bob = register("fbob");

		ResponseEntity<FollowResponse> first = restTemplate.exchange(
				url("/api/users/" + bob.userId() + "/follow"), HttpMethod.POST, authed(alice.token()), FollowResponse.class);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(first.getBody().following()).isTrue();
		assertThat(first.getBody().followerCount()).isEqualTo(1L);

		ResponseEntity<FollowResponse> second = restTemplate.exchange(
				url("/api/users/" + bob.userId() + "/follow"), HttpMethod.POST, authed(alice.token()), FollowResponse.class);
		assertThat(second.getBody().following()).isFalse();
		assertThat(second.getBody().followerCount()).isEqualTo(0L);
	}

	@Test
	void followingSelfIsRejected() {
		Registered alice = register("gself");

		ResponseEntity<String> response = restTemplate.exchange(
				url("/api/users/" + alice.userId() + "/follow"), HttpMethod.POST, authed(alice.token()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void followingUnknownUserReturns404() {
		Registered alice = register("gunknown");

		ResponseEntity<String> response = restTemplate.exchange(
				url("/api/users/999999/follow"), HttpMethod.POST, authed(alice.token()), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void followRequiresAuthentication() {
		ResponseEntity<String> response = restTemplate.postForEntity(url("/api/users/1/follow"), null, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void followingListAndFollowersListReflectFollowedByMeForViewer() {
		Registered alice = register("hviewer");
		Registered bob = register("htarget");
		Registered carol = register("hother");

		restTemplate.exchange(url("/api/users/" + bob.userId() + "/follow"), HttpMethod.POST, authed(carol.token()), FollowResponse.class);
		restTemplate.exchange(url("/api/users/" + carol.userId() + "/follow"), HttpMethod.POST, authed(alice.token()), FollowResponse.class);

		ResponseEntity<Map> followers = restTemplate.exchange(
				url("/api/users/" + bob.userId() + "/followers"), HttpMethod.GET, authed(alice.token()), Map.class);
		List<Map<String, Object>> followerContent = (List<Map<String, Object>>) followers.getBody().get("content");
		assertThat(followerContent).hasSize(1);
		assertThat(followerContent.get(0).get("userId")).isEqualTo(carol.userId().intValue());
		assertThat(followerContent.get(0).get("followedByMe")).isEqualTo(true);

		ResponseEntity<Map> following = restTemplate.exchange(
				url("/api/users/" + carol.userId() + "/following"), HttpMethod.GET, authed(alice.token()), Map.class);
		List<Map<String, Object>> followingContent = (List<Map<String, Object>>) following.getBody().get("content");
		assertThat(followingContent).hasSize(1);
		assertThat(followingContent.get(0).get("userId")).isEqualTo(bob.userId().intValue());
		assertThat(followingContent.get(0).get("followedByMe")).isEqualTo(false);
	}
}
