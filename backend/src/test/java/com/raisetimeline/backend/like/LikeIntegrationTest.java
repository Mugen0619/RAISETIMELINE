package com.raisetimeline.backend.like;

import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.backend.auth.AuthResponse;
import com.raisetimeline.backend.auth.RegisterRequest;
import com.raisetimeline.backend.post.PostRequest;
import com.raisetimeline.backend.post.PostResponse;
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
class LikeIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private LikeRepository likeRepository;

	private String url(String path) {
		return "http://localhost:" + port + path;
	}

	private String registerAndGetAccessToken(String username) {
		RegisterRequest request = new RegisterRequest(username, username + "@example.com", "password123", username);
		ResponseEntity<AuthResponse> response = restTemplate.postForEntity(url("/api/auth/register"), request, AuthResponse.class);
		return response.getBody().accessToken();
	}

	private HttpEntity<Void> authedNoBody(String accessToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		return new HttpEntity<>(headers);
	}

	private Long createPost(String ownerToken, String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(ownerToken);
		ResponseEntity<PostResponse> created = restTemplate.exchange(
				url("/api/posts"), HttpMethod.POST, new HttpEntity<>(new PostRequest(body, null), headers), PostResponse.class);
		return created.getBody().id();
	}

	private ResponseEntity<LikeResponse> toggle(Long postId, String accessToken) {
		return restTemplate.exchange(
				url("/api/posts/" + postId + "/likes"), HttpMethod.POST, authedNoBody(accessToken), LikeResponse.class);
	}

	@Test
	void togglingLikeTwiceLikesThenUnlikesWithoutDuplicateRows() {
		String ownerToken = registerAndGetAccessToken("alice");
		String likerToken = registerAndGetAccessToken("bob");
		Long postId = createPost(ownerToken, "hello world");

		ResponseEntity<LikeResponse> firstToggle = toggle(postId, likerToken);
		assertThat(firstToggle.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(firstToggle.getBody().liked()).isTrue();
		assertThat(firstToggle.getBody().likeCount()).isEqualTo(1L);
		assertThat(likeRepository.countByPostId(postId)).isEqualTo(1L);

		ResponseEntity<LikeResponse> secondToggle = toggle(postId, likerToken);
		assertThat(secondToggle.getBody().liked()).isFalse();
		assertThat(secondToggle.getBody().likeCount()).isEqualTo(0L);
		assertThat(likeRepository.countByPostId(postId)).isZero();

		ResponseEntity<LikeResponse> thirdToggle = toggle(postId, likerToken);
		assertThat(thirdToggle.getBody().liked()).isTrue();
		assertThat(thirdToggle.getBody().likeCount()).isEqualTo(1L);
	}

	@Test
	void likeIsReflectedInPostDetailForTheLikingUserOnly() {
		String ownerToken = registerAndGetAccessToken("carol");
		String likerToken = registerAndGetAccessToken("dave");
		Long postId = createPost(ownerToken, "hello world");

		toggle(postId, likerToken);

		ResponseEntity<PostResponse> likerView = restTemplate.exchange(
				url("/api/posts/" + postId), HttpMethod.GET, authedNoBody(likerToken), PostResponse.class);
		assertThat(likerView.getBody().likedByMe()).isTrue();
		assertThat(likerView.getBody().likeCount()).isEqualTo(1L);

		ResponseEntity<PostResponse> ownerView = restTemplate.exchange(
				url("/api/posts/" + postId), HttpMethod.GET, authedNoBody(ownerToken), PostResponse.class);
		assertThat(ownerView.getBody().likedByMe()).isFalse();
		assertThat(ownerView.getBody().likeCount()).isEqualTo(1L);
	}

	@Test
	void differentUsersCanBothLikeTheSamePost() {
		String ownerToken = registerAndGetAccessToken("erin");
		String likerOneToken = registerAndGetAccessToken("frank");
		String likerTwoToken = registerAndGetAccessToken("grace");
		Long postId = createPost(ownerToken, "hello world");

		toggle(postId, likerOneToken);
		ResponseEntity<LikeResponse> second = toggle(postId, likerTwoToken);

		assertThat(second.getBody().likeCount()).isEqualTo(2L);
		assertThat(likeRepository.countByPostId(postId)).isEqualTo(2L);
	}

	@Test
	void likingOwnPostIsAllowed() {
		String ownerToken = registerAndGetAccessToken("heidi");
		Long postId = createPost(ownerToken, "hello world");

		ResponseEntity<LikeResponse> response = toggle(postId, ownerToken);

		assertThat(response.getBody().liked()).isTrue();
	}

	@Test
	void toggleLikeReturns404ForUnknownPost() {
		String token = registerAndGetAccessToken("ivan");

		ResponseEntity<String> response = restTemplate.exchange(
				url("/api/posts/999999/likes"), HttpMethod.POST, authedNoBody(token), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void toggleLikeRequiresAuthentication() {
		ResponseEntity<String> response = restTemplate.postForEntity(url("/api/posts/1/likes"), null, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}
}
