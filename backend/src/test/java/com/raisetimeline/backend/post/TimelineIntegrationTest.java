package com.raisetimeline.backend.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.backend.auth.AuthResponse;
import com.raisetimeline.backend.auth.RegisterRequest;
import com.raisetimeline.backend.follow.FollowResponse;
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
class TimelineIntegrationTest {

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

	private void follow(Long targetUserId, String followerToken) {
		restTemplate.exchange(url("/api/users/" + targetUserId + "/follow"), HttpMethod.POST,
				authedNoBody(followerToken), FollowResponse.class);
	}

	private Long createPost(String body, String token) {
		ResponseEntity<PostResponse> response = restTemplate.exchange(
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest(body, null), token), PostResponse.class);
		return response.getBody().id();
	}

	@Test
	void followingTimelineReturnsOnlyPostsFromFolloweesNewestFirst() throws InterruptedException {
		Registered viewer = register("tlviewer");
		Registered followedA = register("tlfolloweda");
		Registered followedB = register("tlfollowedb");
		Registered notFollowed = register("tlnotfollowed");

		follow(followedA.userId(), viewer.token());
		follow(followedB.userId(), viewer.token());

		createPost("post from not-followed user", notFollowed.token());
		Thread.sleep(5);
		createPost("first post from followed A", followedA.token());
		Thread.sleep(5);
		createPost("first post from followed B", followedB.token());
		Thread.sleep(5);
		createPost("second post from followed A", followedA.token());

		ResponseEntity<Map> response = restTemplate.exchange(
				url("/api/timeline/following?page=0&size=20"), HttpMethod.GET, authedNoBody(viewer.token()), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
		List<String> bodies = content.stream().map(post -> String.valueOf(post.get("body"))).toList();

		assertThat(bodies).containsExactly(
				"second post from followed A",
				"first post from followed B",
				"first post from followed A");
		assertThat(bodies).doesNotContain("post from not-followed user");
	}

	@Test
	void followingTimelineSupportsPagination() throws InterruptedException {
		Registered viewer = register("tlpageviewer");
		Registered followed = register("tlpagefollowed");
		follow(followed.userId(), viewer.token());

		for (int i = 0; i < 3; i++) {
			createPost("page-marker-" + i, followed.token());
			Thread.sleep(5);
		}

		ResponseEntity<Map> firstPage = restTemplate.exchange(
				url("/api/timeline/following?page=0&size=2"), HttpMethod.GET, authedNoBody(viewer.token()), Map.class);
		List<Map<String, Object>> firstContent = (List<Map<String, Object>>) firstPage.getBody().get("content");
		assertThat(firstContent).hasSize(2);
		assertThat(firstContent.get(0).get("body")).isEqualTo("page-marker-2");

		ResponseEntity<Map> secondPage = restTemplate.exchange(
				url("/api/timeline/following?page=1&size=2"), HttpMethod.GET, authedNoBody(viewer.token()), Map.class);
		List<Map<String, Object>> secondContent = (List<Map<String, Object>>) secondPage.getBody().get("content");
		assertThat(secondContent).hasSize(1);
		assertThat(secondContent.get(0).get("body")).isEqualTo("page-marker-0");
	}

	@Test
	void followingTimelineReturnsEmptyContentWhenNotFollowingAnyone() {
		Registered viewer = register("tlemptyviewer");

		ResponseEntity<Map> response = restTemplate.exchange(
				url("/api/timeline/following?page=0&size=20"), HttpMethod.GET, authedNoBody(viewer.token()), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
		assertThat(content).isEmpty();
	}

	@Test
	void followingTimelineRequiresAuthentication() {
		ResponseEntity<String> response = restTemplate.getForEntity(url("/api/timeline/following"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}
}
