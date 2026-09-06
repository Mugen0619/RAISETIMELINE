package com.raisetimeline.backend.post;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.backend.security.JwtService;
import com.raisetimeline.backend.user.User;
import com.raisetimeline.backend.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * /api/posts配下のエンドポイントが、JWTの有無・正当性によって
 * アクセス制御されることを検証する(なし/不正で401、正当なJWTで通過する)。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private UserRepository userRepository;

	@Test
	void createPostWithoutTokenIsRejected() throws Exception {
		mockMvc.perform(post("/api/posts")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"body\":\"hello\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void getTimelineWithoutTokenIsRejected() throws Exception {
		mockMvc.perform(get("/api/posts"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void updatePostWithoutTokenIsRejected() throws Exception {
		mockMvc.perform(put("/api/posts/{id}", 1L)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"body\":\"hello\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void deletePostWithoutTokenIsRejected() throws Exception {
		mockMvc.perform(delete("/api/posts/{id}", 1L))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void getTimelineWithInvalidTokenIsRejected() throws Exception {
		when(jwtService.isValid("invalid-token")).thenReturn(false);

		mockMvc.perform(get("/api/posts").header("Authorization", "Bearer invalid-token"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void getTimelineWithValidTokenIsAccepted() throws Exception {
		User user = new User("dave", "dave@example.com", "hashed-password", "Dave");
		ReflectionTestUtils.setField(user, "id", 1L);

		when(jwtService.isValid("valid-token")).thenReturn(true);
		when(jwtService.extractUserId("valid-token")).thenReturn(1L);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));

		mockMvc.perform(get("/api/posts").header("Authorization", "Bearer valid-token"))
				.andExpect(status().isOk());
	}
}
