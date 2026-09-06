package com.raisetimeline.backend.like;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.backend.post.PostNotFoundException;
import com.raisetimeline.backend.user.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PostLikeController(いいねトグル)のWeb層テスト。LikeServiceはMockitoでモックする。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LikeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LikeService likeService;

	private static User userWithId(long id, String username) {
		User user = new User(username, username + "@example.com", "hashed", username);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private static Authentication authOf(User user) {
		return new UsernamePasswordAuthenticationToken(user, null, List.of());
	}

	@Test
	void toggleLikeReturnsLikedTrueWithCount() throws Exception {
		User liker = userWithId(2L, "bob");
		when(likeService.toggleLike(eq(100L), eq(liker))).thenReturn(new LikeResponse(100L, true, 1L));

		mockMvc.perform(post("/api/posts/{postId}/likes", 100L).with(authentication(authOf(liker))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked", is(true)))
				.andExpect(jsonPath("$.likeCount", is(1)));
	}

	@Test
	void toggleLikeReturnsLikedFalseWhenUnliking() throws Exception {
		User liker = userWithId(2L, "bob");
		when(likeService.toggleLike(eq(100L), eq(liker))).thenReturn(new LikeResponse(100L, false, 0L));

		mockMvc.perform(post("/api/posts/{postId}/likes", 100L).with(authentication(authOf(liker))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked", is(false)))
				.andExpect(jsonPath("$.likeCount", is(0)));
	}

	@Test
	void toggleLikeReturns404ForUnknownPost() throws Exception {
		User liker = userWithId(2L, "bob");
		when(likeService.toggleLike(eq(999L), eq(liker))).thenThrow(new PostNotFoundException("post not found: 999"));

		mockMvc.perform(post("/api/posts/{postId}/likes", 999L).with(authentication(authOf(liker))))
				.andExpect(status().isNotFound());
	}
}
