package com.raisetimeline.backend.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.backend.auth.AuthResponse;
import com.raisetimeline.backend.auth.RegisterRequest;
import com.raisetimeline.backend.comment.CommentRequest;
import com.raisetimeline.backend.comment.CommentResponse;
import com.raisetimeline.backend.like.LikeResponse;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

/**
 * タイムライン取得APIが、投稿件数に比例してSQLクエリを発行しない(N+1にならない)ことを、
 * Hibernateの統計情報(発行されたPreparedStatement数)で検証する。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@SuppressWarnings({ "rawtypes", "unchecked" })
class PostTimelineQueryCountTest {

	private static final int POST_COUNT = 5;

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

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
	void timelineQueryCountStaysConstantRegardlessOfCommentsAndLikesPerPost() {
		String ownerToken = registerAndGetAccessToken("statsowner");
		String otherToken = registerAndGetAccessToken("statsother");

		String bodyMarker = "qc-marker-post-";
		List<Long> postIds = new ArrayList<>();
		for (int i = 0; i < POST_COUNT; i++) {
			ResponseEntity<PostResponse> created = restTemplate.exchange(
					url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest(bodyMarker + i), ownerToken), PostResponse.class);
			postIds.add(created.getBody().id());
		}

		for (Long postId : postIds) {
			restTemplate.exchange(url("/api/posts/" + postId + "/comments"), HttpMethod.POST,
					authedBody(new CommentRequest("nice"), otherToken), CommentResponse.class);
			restTemplate.exchange(url("/api/posts/" + postId + "/comments"), HttpMethod.POST,
					authedBody(new CommentRequest("great"), otherToken), CommentResponse.class);
			restTemplate.exchange(url("/api/posts/" + postId + "/likes"), HttpMethod.POST,
					authedNoBody(otherToken), LikeResponse.class);
		}

		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		ResponseEntity<Map> timeline = restTemplate.exchange(
				url("/api/posts?page=0&size=20"), HttpMethod.GET, authedNoBody(ownerToken), Map.class);

		assertThat(timeline.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Map<String, Object>> content = (List<Map<String, Object>>) timeline.getBody().get("content");
		List<Map<String, Object>> ourPosts = content.stream()
				.filter(post -> String.valueOf(post.get("body")).startsWith(bodyMarker))
				.toList();
		assertThat(ourPosts).hasSize(POST_COUNT);
		assertThat(ourPosts).allSatisfy(post -> {
			assertThat(((Number) post.get("commentCount")).longValue()).isEqualTo(2L);
			assertThat(((Number) post.get("likeCount")).longValue()).isEqualTo(1L);
		});

		long queryCount = statistics.getPrepareStatementCount();
		assertThat(queryCount)
				.as("timeline query count should stay small/constant, not grow proportionally with the number of posts (%d posts)", POST_COUNT)
				.isLessThanOrEqualTo(8);
	}
}
