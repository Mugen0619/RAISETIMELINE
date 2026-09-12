package com.raisetimeline.backend.image;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.backend.user.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * ImagePresignControllerのWeb層テスト。ImagePresignServiceはMockitoでモックし、
 * HTTPステータス・レスポンス形式・例外のマッピングのみを検証する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImagePresignControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ImagePresignService imagePresignService;

	private static User userWithId(long id, String username) {
		User user = new User(username, username + "@example.com", "hashed", username);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private static Authentication authOf(User user) {
		return new UsernamePasswordAuthenticationToken(user, null, List.of());
	}

	@Test
	void presignReturns200WithUploadUrlAndImageUrl() throws Exception {
		User author = userWithId(1L, "alice");
		when(imagePresignService.presign(any(PresignRequest.class)))
				.thenReturn(new PresignResponse("https://bucket.s3.amazonaws.com/posts/x.jpg?signed", "https://bucket.s3.amazonaws.com/posts/x.jpg"));

		mockMvc.perform(post("/api/posts/images/presign")
						.with(authentication(authOf(author)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new PresignRequest("image/jpeg", 1024))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.uploadUrl", is("https://bucket.s3.amazonaws.com/posts/x.jpg?signed")))
				.andExpect(jsonPath("$.imageUrl", is("https://bucket.s3.amazonaws.com/posts/x.jpg")));
	}

	@Test
	void presignReturns400WhenServiceRejectsUnsupportedContentType() throws Exception {
		User author = userWithId(1L, "alice");
		when(imagePresignService.presign(any(PresignRequest.class)))
				.thenThrow(new InvalidImageException("unsupported content type: image/gif"));

		mockMvc.perform(post("/api/posts/images/presign")
						.with(authentication(authOf(author)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new PresignRequest("image/gif", 1024))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void presignReturns400WhenContentTypeIsBlank() throws Exception {
		User author = userWithId(1L, "alice");

		mockMvc.perform(post("/api/posts/images/presign")
						.with(authentication(authOf(author)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new PresignRequest("", 1024))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void presignReturns400WhenFileSizeIsNotPositive() throws Exception {
		User author = userWithId(1L, "alice");

		mockMvc.perform(post("/api/posts/images/presign")
						.with(authentication(authOf(author)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new PresignRequest("image/jpeg", 0))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void presignReturns401WithoutAuthentication() throws Exception {
		mockMvc.perform(post("/api/posts/images/presign")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new PresignRequest("image/jpeg", 1024))))
				.andExpect(status().isUnauthorized());
	}
}
