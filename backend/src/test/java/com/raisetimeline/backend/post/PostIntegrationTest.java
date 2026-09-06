package com.raisetimeline.backend.post;

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
class PostIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private PostRepository postRepository;

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

	@Test
	void createPostSucceedsForAuthenticatedUserAndPersists() {
		String token = registerAndGetAccessToken("alice");

		ResponseEntity<PostResponse> response = restTemplate.exchange(
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest("hello world"), token), PostResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().body()).isEqualTo("hello world");
		assertThat(response.getBody().username()).isEqualTo("alice");

		assertThat(postRepository.findById(response.getBody().id())).isPresent();
	}

	@Test
	void createPostRejectsBlankAndTooLongBody() {
		String token = registerAndGetAccessToken("bob");

		ResponseEntity<String> blank = restTemplate.exchange(
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest(""), token), String.class);
		assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		ResponseEntity<String> tooLong = restTemplate.exchange(
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest("a".repeat(281)), token), String.class);
		assertThat(tooLong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void createPostRequiresAuthentication() {
		ResponseEntity<String> response = restTemplate.postForEntity(
				url("/api/posts"), new PostRequest("hello"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void timelineReturnsPostsNewestFirstWithPagination() throws InterruptedException {
		String token = registerAndGetAccessToken("carol");

		for (String body : List.of("first post", "second post", "third post")) {
			restTemplate.exchange(url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest(body), token), PostResponse.class);
			Thread.sleep(5);
		}

		ResponseEntity<Map> firstPage = restTemplate.exchange(
				url("/api/posts?page=0&size=2"), HttpMethod.GET, authedNoBody(token), Map.class);

		assertThat(firstPage.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Map<String, Object>> content = (List<Map<String, Object>>) firstPage.getBody().get("content");
		assertThat(content).hasSize(2);
		assertThat(content.get(0).get("body")).isEqualTo("third post");
		assertThat(content.get(1).get("body")).isEqualTo("second post");

		Map<String, Object> page = (Map<String, Object>) firstPage.getBody().get("page");
		assertThat(((Number) page.get("totalElements")).longValue()).isGreaterThanOrEqualTo(3);

		ResponseEntity<Map> secondPage = restTemplate.exchange(
				url("/api/posts?page=1&size=2"), HttpMethod.GET, authedNoBody(token), Map.class);
		List<Map<String, Object>> secondContent = (List<Map<String, Object>>) secondPage.getBody().get("content");
		assertThat(secondContent.get(0).get("body")).isEqualTo("first post");
	}

	@Test
	void updatePostSucceedsForAuthorAndForbiddenForOtherUser() throws InterruptedException {
		String ownerToken = registerAndGetAccessToken("dave");
		String otherToken = registerAndGetAccessToken("erin");

		ResponseEntity<PostResponse> created = restTemplate.exchange(
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest("original body"), ownerToken), PostResponse.class);
		Long postId = created.getBody().id();
		Thread.sleep(5);

		ResponseEntity<PostResponse> ownerUpdate = restTemplate.exchange(
				url("/api/posts/" + postId), HttpMethod.PUT, authedBody(new PostRequest("updated body"), ownerToken), PostResponse.class);
		assertThat(ownerUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(ownerUpdate.getBody().body()).isEqualTo("updated body");
		assertThat(ownerUpdate.getBody().updatedAt()).isAfter(created.getBody().updatedAt());

		ResponseEntity<String> otherUpdate = restTemplate.exchange(
				url("/api/posts/" + postId), HttpMethod.PUT, authedBody(new PostRequest("hijacked"), otherToken), String.class);
		assertThat(otherUpdate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		assertThat(postRepository.findById(postId).orElseThrow().getBody()).isEqualTo("updated body");
	}

	@Test
	void deletePostSucceedsForAuthorAndForbiddenForOtherUser() {
		String ownerToken = registerAndGetAccessToken("frank");
		String otherToken = registerAndGetAccessToken("grace");

		ResponseEntity<PostResponse> created = restTemplate.exchange(
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest("to be deleted"), ownerToken), PostResponse.class);
		Long postId = created.getBody().id();

		ResponseEntity<String> otherDelete = restTemplate.exchange(
				url("/api/posts/" + postId), HttpMethod.DELETE, authedNoBody(otherToken), String.class);
		assertThat(otherDelete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(postRepository.findById(postId)).isPresent();

		ResponseEntity<Void> ownerDelete = restTemplate.exchange(
				url("/api/posts/" + postId), HttpMethod.DELETE, authedNoBody(ownerToken), Void.class);
		assertThat(ownerDelete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(postRepository.findById(postId)).isEmpty();
	}

	@Test
	void updateAndDeleteReturn404ForUnknownPost() {
		String token = registerAndGetAccessToken("heidi");

		ResponseEntity<String> update = restTemplate.exchange(
				url("/api/posts/999999"), HttpMethod.PUT, authedBody(new PostRequest("body"), token), String.class);
		assertThat(update.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		ResponseEntity<String> delete = restTemplate.exchange(
				url("/api/posts/999999"), HttpMethod.DELETE, authedNoBody(token), String.class);
		assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}
}
