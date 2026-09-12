package com.raisetimeline.backend.comment;

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
@SuppressWarnings("rawtypes")
class CommentIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	private String url(String path) {
		return "http://localhost:" + port + path;
	}

	private String registerAndGetAccessToken(String username) {
		RegisterRequest request = new RegisterRequest(username, username + "@example.com", "password123", username);
		ResponseEntity<AuthResponse> response = restTemplate.postForEntity(url("/api/auth/register"), request, AuthResponse.class);
		return response.getBody().accessToken();
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

	private Long createPost(String ownerToken, String body) {
		ResponseEntity<PostResponse> created = restTemplate.exchange(
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest(body, null), ownerToken), PostResponse.class);
		return created.getBody().id();
	}

	@Test
	void createCommentByOtherUserSucceedsAndUpdatesPostCommentCount() {
		String ownerToken = registerAndGetAccessToken("alice");
		String commenterToken = registerAndGetAccessToken("bob");
		Long postId = createPost(ownerToken, "hello world");

		ResponseEntity<CommentResponse> response = restTemplate.exchange(
				url("/api/posts/" + postId + "/comments"), HttpMethod.POST,
				authedBody(new CommentRequest("nice post"), commenterToken), CommentResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody().body()).isEqualTo("nice post");
		assertThat(response.getBody().username()).isEqualTo("bob");

		ResponseEntity<PostResponse> detail = restTemplate.exchange(
				url("/api/posts/" + postId), HttpMethod.GET, authedNoBody(ownerToken), PostResponse.class);
		assertThat(detail.getBody().commentCount()).isEqualTo(1L);
	}

	@Test
	void createCommentOnOwnPostIsAllowed() {
		String ownerToken = registerAndGetAccessToken("carol");
		Long postId = createPost(ownerToken, "my own post");

		ResponseEntity<CommentResponse> response = restTemplate.exchange(
				url("/api/posts/" + postId + "/comments"), HttpMethod.POST,
				authedBody(new CommentRequest("commenting on my own post"), ownerToken), CommentResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}

	@Test
	void createCommentRejectsBlankAndTooLongBody() {
		String ownerToken = registerAndGetAccessToken("dave");
		Long postId = createPost(ownerToken, "post");

		ResponseEntity<String> blank = restTemplate.exchange(
				url("/api/posts/" + postId + "/comments"), HttpMethod.POST,
				authedBody(new CommentRequest(""), ownerToken), String.class);
		assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		ResponseEntity<String> tooLong = restTemplate.exchange(
				url("/api/posts/" + postId + "/comments"), HttpMethod.POST,
				authedBody(new CommentRequest("a".repeat(281)), ownerToken), String.class);
		assertThat(tooLong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void createCommentReturns404ForUnknownPost() {
		String token = registerAndGetAccessToken("erin");

		ResponseEntity<String> response = restTemplate.exchange(
				url("/api/posts/999999/comments"), HttpMethod.POST,
				authedBody(new CommentRequest("body"), token), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void createCommentRequiresAuthentication() {
		ResponseEntity<String> response = restTemplate.postForEntity(
				url("/api/posts/1/comments"), new CommentRequest("body"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void getCommentsReturnsCommentsOldestFirst() throws InterruptedException {
		String ownerToken = registerAndGetAccessToken("ivan");
		String commenterToken = registerAndGetAccessToken("judy");
		Long postId = createPost(ownerToken, "post with comments");

		restTemplate.exchange(url("/api/posts/" + postId + "/comments"), HttpMethod.POST,
				authedBody(new CommentRequest("first comment"), commenterToken), CommentResponse.class);
		Thread.sleep(5);
		restTemplate.exchange(url("/api/posts/" + postId + "/comments"), HttpMethod.POST,
				authedBody(new CommentRequest("second comment"), commenterToken), CommentResponse.class);

		ResponseEntity<Map> response = restTemplate.exchange(
				url("/api/posts/" + postId + "/comments"), HttpMethod.GET, authedNoBody(ownerToken), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
		assertThat(content).hasSize(2);
		assertThat(content.get(0).get("body")).isEqualTo("first comment");
		assertThat(content.get(1).get("body")).isEqualTo("second comment");
	}

	@Test
	void getCommentsReturns404ForUnknownPost() {
		String token = registerAndGetAccessToken("kevin");

		ResponseEntity<String> response = restTemplate.exchange(
				url("/api/posts/999999/comments"), HttpMethod.GET, authedNoBody(token), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void deleteCommentSucceedsForAuthorAndForbiddenForPostOwner() {
		String ownerToken = registerAndGetAccessToken("frank");
		String commenterToken = registerAndGetAccessToken("grace");
		Long postId = createPost(ownerToken, "post");

		ResponseEntity<CommentResponse> created = restTemplate.exchange(
				url("/api/posts/" + postId + "/comments"), HttpMethod.POST,
				authedBody(new CommentRequest("a comment"), commenterToken), CommentResponse.class);
		Long commentId = created.getBody().id();

		ResponseEntity<String> ownerDelete = restTemplate.exchange(
				url("/api/comments/" + commentId), HttpMethod.DELETE, authedNoBody(ownerToken), String.class);
		assertThat(ownerDelete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		ResponseEntity<Void> commenterDelete = restTemplate.exchange(
				url("/api/comments/" + commentId), HttpMethod.DELETE, authedNoBody(commenterToken), Void.class);
		assertThat(commenterDelete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		ResponseEntity<PostResponse> detail = restTemplate.exchange(
				url("/api/posts/" + postId), HttpMethod.GET, authedNoBody(ownerToken), PostResponse.class);
		assertThat(detail.getBody().commentCount()).isZero();
	}

	@Test
	void deleteCommentReturns404ForUnknownComment() {
		String token = registerAndGetAccessToken("heidi");

		ResponseEntity<String> response = restTemplate.exchange(
				url("/api/comments/999999"), HttpMethod.DELETE, authedNoBody(token), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void deleteCommentRequiresAuthentication() {
		ResponseEntity<String> response = restTemplate.exchange(
				url("/api/comments/1"), HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}
}
