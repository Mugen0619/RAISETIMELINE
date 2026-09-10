package com.raisetimeline.backend.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.backend.auth.AuthResponse;
import com.raisetimeline.backend.auth.RegisterRequest;
import java.util.ArrayList;
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
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@SuppressWarnings({ "unchecked", "rawtypes" })
class UserSearchIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private UserRepository userRepository;

	private String url(String path) {
		return "http://localhost:" + port + path;
	}

	private String register(String username, String displayName) {
		RegisterRequest request = new RegisterRequest(username, username + "@example.com", "password123", displayName);
		ResponseEntity<AuthResponse> response = restTemplate.postForEntity(url("/api/auth/register"), request, AuthResponse.class);
		return response.getBody().accessToken();
	}

	private HttpEntity<Void> authedNoBody(String accessToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		return new HttpEntity<>(headers);
	}

	private ResponseEntity<Map> search(String query, String accessToken) {
		String searchUrl = UriComponentsBuilder.fromUriString(url("/api/users/search"))
				.queryParam("q", query)
				.build()
				.toUriString();
		return restTemplate.exchange(searchUrl, HttpMethod.GET, authedNoBody(accessToken), Map.class);
	}

	private List<String> usernamesFrom(ResponseEntity<Map> response) {
		List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
		return content.stream().map(entry -> (String) entry.get("username")).toList();
	}

	@Test
	void searchMatchesPartialUsername() {
		String token = register("uszalice", "Someone Else");
		register("uszalicia", "Another Person");
		register("uszbob", "Bob Builder");

		ResponseEntity<Map> response = search("uszali", token);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(usernamesFrom(response)).containsExactlyInAnyOrder("uszalice", "uszalicia");
	}

	@Test
	void searchMatchesPartialDisplayName() {
		String token = register("uszcarol", "Carol Danvers");
		register("uszdave", "Dave Danvers");
		register("uszerin", "Erin Someone");

		ResponseEntity<Map> response = search("Danvers", token);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(usernamesFrom(response)).containsExactlyInAnyOrder("uszcarol", "uszdave");
	}

	@Test
	void searchPaginationIsStableAcrossPagesWithNoDuplicatesOrGaps() {
		String marker = "uszpage";
		String token = register(marker + "1", marker);
		for (int i = 2; i <= 5; i++) {
			register(marker + i, marker);
		}

		String firstPageUrl = UriComponentsBuilder.fromUriString(url("/api/users/search"))
				.queryParam("q", marker)
				.queryParam("page", 0)
				.queryParam("size", 2)
				.build()
				.toUriString();
		String secondPageUrl = UriComponentsBuilder.fromUriString(url("/api/users/search"))
				.queryParam("q", marker)
				.queryParam("page", 1)
				.queryParam("size", 2)
				.build()
				.toUriString();
		String thirdPageUrl = UriComponentsBuilder.fromUriString(url("/api/users/search"))
				.queryParam("q", marker)
				.queryParam("page", 2)
				.queryParam("size", 2)
				.build()
				.toUriString();

		List<String> firstPage = usernamesFrom(restTemplate.exchange(firstPageUrl, HttpMethod.GET, authedNoBody(token), Map.class));
		List<String> secondPage = usernamesFrom(restTemplate.exchange(secondPageUrl, HttpMethod.GET, authedNoBody(token), Map.class));
		List<String> thirdPage = usernamesFrom(restTemplate.exchange(thirdPageUrl, HttpMethod.GET, authedNoBody(token), Map.class));

		List<String> allSeen = new ArrayList<>();
		allSeen.addAll(firstPage);
		allSeen.addAll(secondPage);
		allSeen.addAll(thirdPage);

		assertThat(allSeen).hasSize(5);
		assertThat(allSeen).doesNotHaveDuplicates();
		assertThat(allSeen).containsExactlyInAnyOrder(marker + "1", marker + "2", marker + "3", marker + "4", marker + "5");
	}

	@Test
	void searchIsCaseInsensitive() {
		String token = register("uszfrank", "Frank N Stein");

		ResponseEntity<Map> response = search("FRANK", token);

		assertThat(usernamesFrom(response)).contains("uszfrank");
	}

	@Test
	void searchReturnsEmptyArrayWhenNoMatch() {
		String token = register("uszgrace", "Grace Hopper");

		ResponseEntity<Map> response = search("nonexistentkeywordxyz", token);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(usernamesFrom(response)).isEmpty();
	}

	@Test
	void searchWithSqlInjectionAttemptDoesNotErrorAndDoesNotDestroyData() {
		String token = register("uszheidi", "Heidi Injection");

		ResponseEntity<Map> response = search("'; DROP TABLE users; --", token);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(usernamesFrom(response)).isEmpty();

		// usersテーブルが実際に破壊されていないことを確認する(存在確認+既存ユーザーがまだ検索できること)
		assertThat(userRepository.existsByUsername("uszheidi")).isTrue();
		ResponseEntity<Map> stillWorks = search("uszheidi", token);
		assertThat(stillWorks.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(usernamesFrom(stillWorks)).contains("uszheidi");
	}

	@Test
	void searchWithBlankQueryReturnsEmptyArray() {
		String token = register("uszivan", "Ivan Search");

		ResponseEntity<Map> response = search("   ", token);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(usernamesFrom(response)).isEmpty();
	}

	@Test
	void searchRequiresAuthentication() {
		ResponseEntity<String> response = restTemplate.getForEntity(url("/api/users/search?q=test"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}
}
