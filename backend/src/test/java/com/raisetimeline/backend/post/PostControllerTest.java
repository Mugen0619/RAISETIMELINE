package com.raisetimeline.backend.post;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.backend.user.User;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * PostControllerのWeb層テスト。PostServiceはMockitoでモックし、
 * バリデーション・HTTPステータス・例外のマッピングのみを検証する。
 * リクエストの認証はSecurityMockMvcRequestPostProcessors.authentication(...)で
 * 直接注入し、JWTの検証自体はPostSecurityTest/PostIntegrationTestで別途検証する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private PostService postService;

	private static User userWithId(long id, String username) {
		User user = new User(username, username + "@example.com", "hashed", username);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private static Authentication authOf(User user) {
		return new UsernamePasswordAuthenticationToken(user, null, List.of());
	}

	@Test
	void createPostReturns201WithBody() throws Exception {
		User author = userWithId(1L, "alice");
		PostResponse response = new PostResponse(100L, 1L, "alice", "Alice", "hello world", Instant.now(), Instant.now());
		when(postService.createPost(eq(author), any(PostRequest.class))).thenReturn(response);

		mockMvc.perform(post("/api/posts")
						.with(authentication(authOf(author)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new PostRequest("hello world"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", is(100)))
				.andExpect(jsonPath("$.body", is("hello world")));
	}

	@Test
	void createPostReturns400ForBlankBody() throws Exception {
		User author = userWithId(1L, "alice");

		mockMvc.perform(post("/api/posts")
						.with(authentication(authOf(author)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new PostRequest(""))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createPostReturns400ForBodyOver280Characters() throws Exception {
		User author = userWithId(1L, "alice");
		String tooLong = "a".repeat(281);

		mockMvc.perform(post("/api/posts")
						.with(authentication(authOf(author)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new PostRequest(tooLong))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void getTimelineReturnsPagedContent() throws Exception {
		User author = userWithId(1L, "alice");
		PostResponse response = new PostResponse(100L, 1L, "alice", "Alice", "hello world", Instant.now(), Instant.now());
		var pageable = PageRequest.of(0, 20);
		when(postService.getTimeline(any())).thenReturn(new PageImpl<>(List.of(response), pageable, 1));

		mockMvc.perform(get("/api/posts").with(authentication(authOf(author))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id", is(100)))
				.andExpect(jsonPath("$.page.totalElements", is(1)));
	}

	@Test
	void updatePostReturns200WithUpdatedBody() throws Exception {
		User author = userWithId(1L, "alice");
		PostResponse response = new PostResponse(100L, 1L, "alice", "Alice", "updated body", Instant.now(), Instant.now());
		when(postService.updatePost(eq(100L), eq(author), any(PostRequest.class))).thenReturn(response);

		mockMvc.perform(put("/api/posts/{id}", 100L)
						.with(authentication(authOf(author)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new PostRequest("updated body"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body", is("updated body")));
	}

	@Test
	void updatePostReturns403WhenNotAuthor() throws Exception {
		User otherUser = userWithId(2L, "bob");
		when(postService.updatePost(eq(100L), eq(otherUser), any(PostRequest.class)))
				.thenThrow(new ForbiddenPostAccessException("only the author can modify this post"));

		mockMvc.perform(put("/api/posts/{id}", 100L)
						.with(authentication(authOf(otherUser)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new PostRequest("hijacked"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void updatePostReturns404ForUnknownPost() throws Exception {
		User author = userWithId(1L, "alice");
		when(postService.updatePost(eq(999L), eq(author), any(PostRequest.class)))
				.thenThrow(new PostNotFoundException("post not found: 999"));

		mockMvc.perform(put("/api/posts/{id}", 999L)
						.with(authentication(authOf(author)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new PostRequest("body"))))
				.andExpect(status().isNotFound());
	}

	@Test
	void deletePostReturns204() throws Exception {
		User author = userWithId(1L, "alice");

		mockMvc.perform(delete("/api/posts/{id}", 100L).with(authentication(authOf(author))))
				.andExpect(status().isNoContent());
	}

	@Test
	void deletePostReturns403WhenNotAuthor() throws Exception {
		User otherUser = userWithId(2L, "bob");
		org.mockito.Mockito.doThrow(new ForbiddenPostAccessException("only the author can modify this post"))
				.when(postService).deletePost(eq(100L), eq(otherUser));

		mockMvc.perform(delete("/api/posts/{id}", 100L).with(authentication(authOf(otherUser))))
				.andExpect(status().isForbidden());
	}
}
