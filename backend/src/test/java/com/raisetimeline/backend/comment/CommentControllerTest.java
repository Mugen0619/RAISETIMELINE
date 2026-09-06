package com.raisetimeline.backend.comment;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.backend.post.PostNotFoundException;
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
 * PostCommentController(コメント投稿)・CommentController(コメント削除)のWeb層テスト。
 * CommentServiceはMockitoでモックし、バリデーション・HTTPステータス・例外のマッピングのみを検証する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommentControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private CommentService commentService;

	private static User userWithId(long id, String username) {
		User user = new User(username, username + "@example.com", "hashed", username);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private static Authentication authOf(User user) {
		return new UsernamePasswordAuthenticationToken(user, null, List.of());
	}

	@Test
	void createCommentReturns201WithBody() throws Exception {
		User commenter = userWithId(2L, "bob");
		CommentResponse response = new CommentResponse(500L, 100L, 2L, "bob", "Bob", "nice post", Instant.now());
		when(commentService.createComment(eq(100L), eq(commenter), any(CommentRequest.class))).thenReturn(response);

		mockMvc.perform(post("/api/posts/{postId}/comments", 100L)
						.with(authentication(authOf(commenter)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CommentRequest("nice post"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", is(500)))
				.andExpect(jsonPath("$.body", is("nice post")));
	}

	@Test
	void createCommentReturns400ForBlankBody() throws Exception {
		User commenter = userWithId(2L, "bob");

		mockMvc.perform(post("/api/posts/{postId}/comments", 100L)
						.with(authentication(authOf(commenter)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CommentRequest(""))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createCommentReturns400ForBodyOver280Characters() throws Exception {
		User commenter = userWithId(2L, "bob");

		mockMvc.perform(post("/api/posts/{postId}/comments", 100L)
						.with(authentication(authOf(commenter)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CommentRequest("a".repeat(281)))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createCommentReturns404ForUnknownPost() throws Exception {
		User commenter = userWithId(2L, "bob");
		when(commentService.createComment(eq(999L), eq(commenter), any(CommentRequest.class)))
				.thenThrow(new PostNotFoundException("post not found: 999"));

		mockMvc.perform(post("/api/posts/{postId}/comments", 999L)
						.with(authentication(authOf(commenter)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CommentRequest("body"))))
				.andExpect(status().isNotFound());
	}

	@Test
	void getCommentsReturnsPagedContent() throws Exception {
		User requester = userWithId(1L, "alice");
		CommentResponse response = new CommentResponse(500L, 100L, 2L, "bob", "Bob", "nice post", Instant.now());
		var pageable = PageRequest.of(0, 20);
		when(commentService.getComments(eq(100L), any())).thenReturn(new PageImpl<>(List.of(response), pageable, 1));

		mockMvc.perform(get("/api/posts/{postId}/comments", 100L).with(authentication(authOf(requester))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id", is(500)))
				.andExpect(jsonPath("$.page.totalElements", is(1)));
	}

	@Test
	void getCommentsReturns404ForUnknownPost() throws Exception {
		User requester = userWithId(1L, "alice");
		when(commentService.getComments(eq(999L), any())).thenThrow(new PostNotFoundException("post not found: 999"));

		mockMvc.perform(get("/api/posts/{postId}/comments", 999L).with(authentication(authOf(requester))))
				.andExpect(status().isNotFound());
	}

	@Test
	void deleteCommentReturns204() throws Exception {
		User author = userWithId(1L, "alice");

		mockMvc.perform(delete("/api/comments/{id}", 500L).with(authentication(authOf(author))))
				.andExpect(status().isNoContent());
	}

	@Test
	void deleteCommentReturns403WhenNotAuthor() throws Exception {
		User otherUser = userWithId(2L, "bob");
		org.mockito.Mockito.doThrow(new ForbiddenCommentAccessException("only the author can delete this comment"))
				.when(commentService).deleteComment(eq(500L), eq(otherUser));

		mockMvc.perform(delete("/api/comments/{id}", 500L).with(authentication(authOf(otherUser))))
				.andExpect(status().isForbidden());
	}

	@Test
	void deleteCommentReturns404ForUnknownComment() throws Exception {
		User author = userWithId(1L, "alice");
		org.mockito.Mockito.doThrow(new CommentNotFoundException("comment not found: 999"))
				.when(commentService).deleteComment(eq(999L), eq(author));

		mockMvc.perform(delete("/api/comments/{id}", 999L).with(authentication(authOf(author))))
				.andExpect(status().isNotFound());
	}
}
