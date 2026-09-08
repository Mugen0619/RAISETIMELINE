package com.raisetimeline.backend.follow;

import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.backend.auth.AuthResponse;
import com.raisetimeline.backend.auth.RegisterRequest;
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
 * フォロワー一覧取得APIが、一覧に含まれるユーザー数に比例してSQLクエリを発行しない
 * (「自分がフォローしているか」の判定がN+1にならない)ことを、Hibernateの統計情報
 * (発行されたPreparedStatement数)で検証する。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@SuppressWarnings({ "rawtypes", "unchecked" })
class FollowQueryCountTest {

	private static final int FOLLOWER_COUNT = 8;

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
	void followersQueryCountStaysConstantRegardlessOfListSize() {
		Registered viewer = register("qcviewer");
		Registered target = register("qctarget");

		List<Registered> followers = new ArrayList<>();
		for (int i = 0; i < FOLLOWER_COUNT; i++) {
			Registered follower = register("qcfollower" + i);
			follow(target.userId(), follower.token());
			followers.add(follower);
		}

		// viewer自身も一部のフォロワーをフォローしておき、followedByMeがtrue/false混在することを確認する
		follow(followers.get(0).userId(), viewer.token());
		follow(followers.get(1).userId(), viewer.token());

		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		ResponseEntity<Map> response = restTemplate.exchange(
				url("/api/users/" + target.userId() + "/followers?page=0&size=20"),
				HttpMethod.GET, authedNoBody(viewer.token()), Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
		assertThat(content).hasSize(FOLLOWER_COUNT);

		long followedByMeTrueCount = content.stream().filter(entry -> Boolean.TRUE.equals(entry.get("followedByMe"))).count();
		assertThat(followedByMeTrueCount).isEqualTo(2L);

		long queryCount = statistics.getPrepareStatementCount();
		assertThat(queryCount)
				.as("followers list query count should stay small/constant, not grow proportionally with the number of followers (%d followers)",
						FOLLOWER_COUNT)
				.isLessThanOrEqualTo(6);
	}
}
