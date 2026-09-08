package com.raisetimeline.backend.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.backend.auth.AuthResponse;
import com.raisetimeline.backend.auth.RegisterRequest;
import com.raisetimeline.backend.comment.CommentRequest;
import com.raisetimeline.backend.comment.CommentResponse;
import com.raisetimeline.backend.follow.FollowResponse;
import com.raisetimeline.backend.like.LikeResponse;
import jakarta.persistence.EntityManagerFactory;
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
 * フォロー中タイムライン取得APIが、フォロー中ユーザー数・各ユーザーの投稿に対する
 * コメント数/いいね数に比例してSQLクエリを発行しない(N+1にならない)ことを、
 * Hibernateの統計情報(発行されたPreparedStatement数)で検証する。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@SuppressWarnings({ "rawtypes", "unchecked" })
class FollowingTimelineQueryCountTest {

	private static final int FOLLOWEE_COUNT = 8;

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

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

	@Test
	void followingTimelineQueryCountStaysConstantRegardlessOfFolloweeCount() {
		Registered viewer = register("ftqcviewer");
		Registered other = register("ftqcother");

		String bodyMarker = "ftqc-marker-post-";
		for (int i = 0; i < FOLLOWEE_COUNT; i++) {
			Registered followee = register("ftqcfollowee" + i);
			follow(followee.userId(), viewer.token());

			ResponseEntity<PostResponse> created = restTemplate.exchange(
					url("/api/posts"), HttpMethod.POST, authedBody(new PostRequest(bodyMarker + i), followee.token()), PostResponse.class);
			Long postId = created.getBody().id();

			restTemplate.exchange(url("/api/posts/" + postId + "/comments"), HttpMethod.POST,
					authedBody(new CommentRequest("nice"), other.token()), CommentResponse.class);
			restTemplate.exchange(url("/api/posts/" + postId + "/comments"), HttpMethod.POST,
					authedBody(new CommentRequest("great"), other.token()), CommentResponse.class);
			restTemplate.exchange(url("/api/posts/" + postId + "/likes"), HttpMethod.POST,
					authedNoBody(other.token()), LikeResponse.class);
		}

		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		ResponseEntity<Map> response = restTemplate.exchange(
				url("/api/timeline/following?page=0&size=20"), HttpMethod.GET, authedNoBody(viewer.token()), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
		List<Map<String, Object>> ourPosts = content.stream()
				.filter(post -> String.valueOf(post.get("body")).startsWith(bodyMarker))
				.toList();
		assertThat(ourPosts).hasSize(FOLLOWEE_COUNT);
		assertThat(ourPosts).allSatisfy(post -> {
			assertThat(((Number) post.get("commentCount")).longValue()).isEqualTo(2L);
			assertThat(((Number) post.get("likeCount")).longValue()).isEqualTo(1L);
		});

		long queryCount = statistics.getPrepareStatementCount();
		assertThat(queryCount)
				.as("following timeline query count should stay small/constant, not grow proportionally with the number of followees (%d followees)",
						FOLLOWEE_COUNT)
				.isLessThanOrEqualTo(8);
	}
}
