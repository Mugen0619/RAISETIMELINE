package com.raisetimeline.backend.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.backend.auth.AuthResponse;
import com.raisetimeline.backend.auth.RegisterRequest;
import com.raisetimeline.backend.comment.CommentRepository;
import com.raisetimeline.backend.comment.CommentRequest;
import com.raisetimeline.backend.comment.CommentResponse;
import com.raisetimeline.backend.like.LikeRepository;
import com.raisetimeline.backend.like.LikeResponse;
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

	@Autowired
	private CommentRepository commentRepository;

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
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest("hello world", null), token), PostResponse.class);

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
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest("", null), token), String.class);
		assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		ResponseEntity<String> tooLong = restTemplate.exchange(
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest("a".repeat(281), null), token), String.class);
		assertThat(tooLong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void createPostRequiresAuthentication() {
		ResponseEntity<String> response = restTemplate.postForEntity(
				url("/api/posts"), new PostRequest("hello", null), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void timelineReturnsPostsNewestFirstWithPagination() throws InterruptedException {
		String token = registerAndGetAccessToken("carol");

		for (String body : List.of("first post", "second post", "third post")) {
			restTemplate.exchange(url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest(body, null), token), PostResponse.class);
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
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest("original body", null), ownerToken), PostResponse.class);
		Long postId = created.getBody().id();
		Thread.sleep(5);

		ResponseEntity<PostResponse> ownerUpdate = restTemplate.exchange(
				url("/api/posts/" + postId), HttpMethod.PUT, authedBody(new PostRequest("updated body", null), ownerToken), PostResponse.class);
		assertThat(ownerUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(ownerUpdate.getBody().body()).isEqualTo("updated body");
		assertThat(ownerUpdate.getBody().updatedAt()).isAfter(created.getBody().updatedAt());

		ResponseEntity<String> otherUpdate = restTemplate.exchange(
				url("/api/posts/" + postId), HttpMethod.PUT, authedBody(new PostRequest("hijacked", null), otherToken), String.class);
		assertThat(otherUpdate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		assertThat(postRepository.findById(postId).orElseThrow().getBody()).isEqualTo("updated body");
	}

	@Test
	void deletePostSucceedsForAuthorAndForbiddenForOtherUser() {
		String ownerToken = registerAndGetAccessToken("frank");
		String otherToken = registerAndGetAccessToken("grace");

		ResponseEntity<PostResponse> created = restTemplate.exchange(
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest("to be deleted", null), ownerToken), PostResponse.class);
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
	void getPostReturnsPostWithCommentAndLikeCounts() {
		String ownerToken = registerAndGetAccessToken("ivan");
		String otherToken = registerAndGetAccessToken("judy");

		ResponseEntity<PostResponse> created = restTemplate.exchange(
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest("detail post", null), ownerToken), PostResponse.class);
		Long postId = created.getBody().id();

		restTemplate.exchange(url("/api/posts/" + postId + "/comments"), HttpMethod.POST,
				authedBody(new CommentRequest("a comment"), otherToken), CommentResponse.class);
		restTemplate.exchange(url("/api/posts/" + postId + "/likes"), HttpMethod.POST, authedNoBody(otherToken), LikeResponse.class);

		ResponseEntity<PostResponse> detail = restTemplate.exchange(
				url("/api/posts/" + postId), HttpMethod.GET, authedNoBody(ownerToken), PostResponse.class);

		assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(detail.getBody().commentCount()).isEqualTo(1L);
		assertThat(detail.getBody().likeCount()).isEqualTo(1L);
		assertThat(detail.getBody().likedByMe()).isFalse();
	}

	@Test
	void getPostReturns404ForUnknownPost() {
		String token = registerAndGetAccessToken("kevin");

		ResponseEntity<String> response = restTemplate.exchange(
				url("/api/posts/999999"), HttpMethod.GET, authedNoBody(token), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void deletingPostCascadesToItsCommentsAndLikes() {
		String ownerToken = registerAndGetAccessToken("laura");
		String otherToken = registerAndGetAccessToken("mike");

		ResponseEntity<PostResponse> created = restTemplate.exchange(
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest("to be deleted with children", null), ownerToken), PostResponse.class);
		Long postId = created.getBody().id();

		restTemplate.exchange(url("/api/posts/" + postId + "/comments"), HttpMethod.POST,
				authedBody(new CommentRequest("a comment"), otherToken), CommentResponse.class);
		restTemplate.exchange(url("/api/posts/" + postId + "/likes"), HttpMethod.POST, authedNoBody(otherToken), LikeResponse.class);

		ResponseEntity<Void> delete = restTemplate.exchange(
				url("/api/posts/" + postId), HttpMethod.DELETE, authedNoBody(ownerToken), Void.class);

		assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(commentRepository.countByPostId(postId)).isZero();
		assertThat(likeRepository.countByPostId(postId)).isZero();
	}

	@Test
	void createPostRejectsMoreThanFourImageUrls() {
		String token = registerAndGetAccessToken("nathan");
		List<String> fiveImageUrls = List.of(
				"https://raisetimeline-test-bucket.s3.ap-northeast-1.amazonaws.com/posts/1.jpg",
				"https://raisetimeline-test-bucket.s3.ap-northeast-1.amazonaws.com/posts/2.jpg",
				"https://raisetimeline-test-bucket.s3.ap-northeast-1.amazonaws.com/posts/3.jpg",
				"https://raisetimeline-test-bucket.s3.ap-northeast-1.amazonaws.com/posts/4.jpg",
				"https://raisetimeline-test-bucket.s3.ap-northeast-1.amazonaws.com/posts/5.jpg");

		ResponseEntity<String> response = restTemplate.exchange(
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest("too many images", fiveImageUrls), token), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void createPostRejectsImageUrlNotFromConfiguredBucket() {
		String token = registerAndGetAccessToken("olivia");
		List<String> untrustedUrls = List.of("https://evil.example.com/image.png");

		ResponseEntity<String> response = restTemplate.exchange(
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest("untrusted image", untrustedUrls), token), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void createPostRejectsEmptyBodyAndNoImages() {
		String token = registerAndGetAccessToken("peter");

		ResponseEntity<String> response = restTemplate.exchange(
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest("", null), token), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void createPostWithTrustedImageUrlsPersistsAndReturnsThemInOrder() {
		String token = registerAndGetAccessToken("quinn");
		List<String> imageUrls = List.of(
				"https://raisetimeline-test-bucket.s3.ap-northeast-1.amazonaws.com/posts/a.jpg",
				"https://raisetimeline-test-bucket.s3.ap-northeast-1.amazonaws.com/posts/b.jpg");

		ResponseEntity<PostResponse> created = restTemplate.exchange(
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest("with images", imageUrls), token), PostResponse.class);

		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(created.getBody().imageUrls()).containsExactlyElementsOf(imageUrls);

		ResponseEntity<PostResponse> fetched = restTemplate.exchange(
				url("/api/posts/" + created.getBody().id()), HttpMethod.GET, authedNoBody(token), PostResponse.class);
		assertThat(fetched.getBody().imageUrls()).containsExactlyElementsOf(imageUrls);
	}

	@Test
	void createPostWithoutBodyButWithImagesPersistsSuccessfully() {
		String token = registerAndGetAccessToken("robert");
		List<String> imageUrls = List.of("https://raisetimeline-test-bucket.s3.ap-northeast-1.amazonaws.com/posts/only.jpg");

		ResponseEntity<PostResponse> created = restTemplate.exchange(
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest(null, imageUrls), token), PostResponse.class);

		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(created.getBody().body()).isNull();
		assertThat(created.getBody().imageUrls()).containsExactlyElementsOf(imageUrls);
		assertThat(postRepository.findById(created.getBody().id())).isPresent();
	}

	@Test
	void deletingPostCascadesToItsImages() {
		String token = registerAndGetAccessToken("rachel");
		List<String> imageUrls = List.of("https://raisetimeline-test-bucket.s3.ap-northeast-1.amazonaws.com/posts/c.jpg");

		ResponseEntity<PostResponse> created = restTemplate.exchange(
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest("to be deleted", imageUrls), token), PostResponse.class);
		Long postId = created.getBody().id();

		ResponseEntity<Void> delete = restTemplate.exchange(
				url("/api/posts/" + postId), HttpMethod.DELETE, authedNoBody(token), Void.class);

		assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		ResponseEntity<String> afterDelete = restTemplate.exchange(
				url("/api/posts/" + postId), HttpMethod.GET, authedNoBody(token), String.class);
		assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void updatePostReplacesImagesWithNewOnes() {
		String token = registerAndGetAccessToken("steve");
		List<String> originalUrls = List.of("https://raisetimeline-test-bucket.s3.ap-northeast-1.amazonaws.com/posts/old.jpg");
		List<String> updatedUrls = List.of("https://raisetimeline-test-bucket.s3.ap-northeast-1.amazonaws.com/posts/new.jpg");

		ResponseEntity<PostResponse> created = restTemplate.exchange(
				url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest("original", originalUrls), token), PostResponse.class);
		Long postId = created.getBody().id();

		ResponseEntity<PostResponse> updated = restTemplate.exchange(
				url("/api/posts/" + postId), HttpMethod.PUT, authedBody(new PostRequest("updated", updatedUrls), token), PostResponse.class);

		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(updated.getBody().imageUrls()).containsExactlyElementsOf(updatedUrls);
	}

	@Test
	void updateAndDeleteReturn404ForUnknownPost() {
		String token = registerAndGetAccessToken("heidi");

		ResponseEntity<String> update = restTemplate.exchange(
				url("/api/posts/999999"), HttpMethod.PUT, authedBody(new PostRequest("body", null), token), String.class);
		assertThat(update.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		ResponseEntity<String> delete = restTemplate.exchange(
				url("/api/posts/999999"), HttpMethod.DELETE, authedNoBody(token), String.class);
		assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}
}
