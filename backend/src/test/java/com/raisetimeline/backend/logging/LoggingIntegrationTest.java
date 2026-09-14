package com.raisetimeline.backend.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.raisetimeline.backend.auth.AuthResponse;
import com.raisetimeline.backend.auth.LoginRequest;
import com.raisetimeline.backend.auth.RefreshRequest;
import com.raisetimeline.backend.auth.RefreshResponse;
import com.raisetimeline.backend.auth.RegisterRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ログ出力の実体(実際にJSONへエンコードした結果)を検証する統合テスト。
 * logbackのListAppenderでイベントを捕捉し、本番と同じLogstashEncoderでエンコードした
 * 文字列に対してアサーションすることで、テスト用のプレーンテキスト出力(logback-spring.xmlの
 * testプロファイル設定)に左右されず、実際に出力されうるJSONの内容を検証する。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class LoggingIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	private Logger rootLogger;
	private ListAppender<ILoggingEvent> listAppender;

	@BeforeEach
	void attachListAppender() {
		rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		listAppender = new ListAppender<>();
		listAppender.setContext(rootLogger.getLoggerContext());
		listAppender.start();
		rootLogger.addAppender(listAppender);
	}

	@AfterEach
	void detachListAppender() {
		rootLogger.detachAppender(listAppender);
	}

	private String url(String path) {
		return "http://localhost:" + port + path;
	}

	private List<String> encodeCapturedEventsAsJson() {
		LogstashEncoder encoder = new LogstashEncoder();
		encoder.setContext(rootLogger.getLoggerContext());
		// logback-spring.xmlの本番用JSON_CONSOLEアペンダーと同じ設定を再現する
		encoder.setIncludeContext(false);
		encoder.setCustomFields("{\"service\":\"raisetimeline-backend\"}");
		encoder.start();
		try {
			return listAppender.list.stream()
					.map(event -> new String(encoder.encode(event), StandardCharsets.UTF_8))
					.toList();
		} finally {
			encoder.stop();
		}
	}

	@Test
	void passwordAndTokensAreNeverLoggedDuringRegisterLoginAndRefresh() {
		String password = "super-secret-pw-12345";
		String wrongPassword = "totally-wrong-password";
		RegisterRequest registerRequest =
				new RegisterRequest("loguser1", "loguser1@example.com", password, "loguser1");

		ResponseEntity<AuthResponse> registerResponse =
				restTemplate.postForEntity(url("/api/auth/register"), registerRequest, AuthResponse.class);
		String accessToken = registerResponse.getBody().accessToken();
		String refreshToken = registerResponse.getBody().refreshToken();

		restTemplate.postForEntity(url("/api/auth/login"), new LoginRequest("loguser1@example.com", password), AuthResponse.class);
		restTemplate.postForEntity(
				url("/api/auth/login"), new LoginRequest("loguser1@example.com", wrongPassword), AuthResponse.class);
		restTemplate.postForEntity(url("/api/auth/refresh"), new RefreshRequest(refreshToken), RefreshResponse.class);

		String combinedLogs = String.join("\n", encodeCapturedEventsAsJson());

		assertThat(combinedLogs).doesNotContain(password);
		assertThat(combinedLogs).doesNotContain(wrongPassword);
		assertThat(combinedLogs).doesNotContain(accessToken);
		assertThat(combinedLogs).doesNotContain(refreshToken);
	}

	@Test
	void sameTraceIdAppearsAcrossAllLogLinesProducedByOneRequest() {
		restTemplate.postForEntity(
				url("/api/auth/login"), new LoginRequest("nonexistent@example.com", "whatever12345"), AuthResponse.class);

		List<String> jsonLogs = encodeCapturedEventsAsJson();
		ObjectMapper mapper = new ObjectMapper();

		List<String> traceIds = jsonLogs.stream()
				.map(mapper::readTree)
				.filter(node -> node.has("traceId"))
				.map(node -> node.get("traceId").asString())
				.distinct()
				.toList();

		// アクセスログ(RequestLoggingFilter)と、認証失敗の警告ログ(GlobalExceptionHandler)の
		// 少なくとも2行が出ているはずで、そのいずれにも同じtraceIdが含まれること
		assertThat(jsonLogs.size()).isGreaterThanOrEqualTo(2);
		assertThat(traceIds).hasSize(1);
		assertThat(traceIds.get(0)).isNotBlank();
	}

	@Test
	void authenticatedRequestIncludesUserIdInAccessLog() {
		RegisterRequest registerRequest =
				new RegisterRequest("loguser2", "loguser2@example.com", "password123456", "loguser2");
		ResponseEntity<AuthResponse> registerResponse =
				restTemplate.postForEntity(url("/api/auth/register"), registerRequest, AuthResponse.class);
		Long userId = registerResponse.getBody().userId();
		String accessToken = registerResponse.getBody().accessToken();
		listAppender.list.clear();

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		restTemplate.exchange(url("/api/posts"), HttpMethod.GET, new HttpEntity<>(headers), String.class);

		ObjectMapper mapper = new ObjectMapper();
		boolean foundMatchingUserId = encodeCapturedEventsAsJson().stream()
				.map(mapper::readTree)
				.filter(node -> node.has("userId"))
				.anyMatch(node -> node.get("userId").asString().equals(String.valueOf(userId)));

		assertThat(foundMatchingUserId).isTrue();
	}

	@Test
	void unauthenticatedRequestDoesNotIncludeUserIdInAnyLogLine() {
		restTemplate.postForEntity(url("/api/auth/register"),
				new RegisterRequest("loguser3", "loguser3@example.com", "password123456", "loguser3"), AuthResponse.class);

		ObjectMapper mapper = new ObjectMapper();
		boolean anyLineHasUserId = encodeCapturedEventsAsJson().stream()
				.map(mapper::readTree)
				.anyMatch(node -> node.has("userId"));

		assertThat(anyLineHasUserId).isFalse();
	}

	@Test
	void accessLogIncludesHttpStatusEndpointMethodAndDuration() {
		restTemplate.postForEntity(url("/api/auth/register"),
				new RegisterRequest("loguser4", "loguser4@example.com", "password123456", "loguser4"), AuthResponse.class);

		ObjectMapper mapper = new ObjectMapper();
		List<JsonNode> accessLogLines = encodeCapturedEventsAsJson().stream()
				.map(mapper::readTree)
				.filter(node -> node.has("httpStatus"))
				.toList();

		assertThat(accessLogLines).isNotEmpty();
		JsonNode accessLog = accessLogLines.get(0);
		assertThat(accessLog.get("httpStatus").asInt()).isEqualTo(201);
		assertThat(accessLog.get("endpoint").asString()).isEqualTo("/api/auth/register");
		assertThat(accessLog.get("method").asString()).isEqualTo("POST");
		assertThat(accessLog.has("duration_ms")).isTrue();
		assertThat(accessLog.get("service").asString()).isEqualTo("raisetimeline-backend");
	}

	@Test
	void exceptionLogIncludesExceptionTypeAndMessage() {
		restTemplate.postForEntity(
				url("/api/auth/login"), new LoginRequest("nonexistent@example.com", "whatever12345"), AuthResponse.class);

		ObjectMapper mapper = new ObjectMapper();
		boolean foundExceptionLog = encodeCapturedEventsAsJson().stream()
				.map(mapper::readTree)
				.filter(node -> node.has("exceptionType"))
				.anyMatch(node -> node.get("exceptionType").asString().equals("InvalidCredentialsException")
						&& Objects.equals(node.get("exceptionMessage").asString(), "invalid email or password"));

		assertThat(foundExceptionLog).isTrue();
	}
}
