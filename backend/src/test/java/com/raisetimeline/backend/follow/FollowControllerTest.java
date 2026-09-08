package com.raisetimeline.backend.follow;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.backend.user.User;
import com.raisetimeline.backend.user.UserNotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UserFollowControllerのWeb層テスト。FollowServiceはMockitoでモックし、
 * HTTPステータス・例外のマッピングのみを検証する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FollowControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private FollowService followService;

	private static User userWithId(long id, String username) {
		User user = new User(username, username + "@example.com", "hashed", username);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private static Authentication authOf(User user) {
		return new UsernamePasswordAuthenticationToken(user, null, List.of());
	}

	@Test
	void toggleFollowReturns200WithFollowingTrue() throws Exception {
		User follower = userWithId(1L, "alice");
		when(followService.toggleFollow(eq(2L), eq(follower))).thenReturn(new FollowResponse(2L, true, 1L));

		mockMvc.perform(post("/api/users/{userId}/follow", 2L).with(authentication(authOf(follower))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.following", is(true)))
				.andExpect(jsonPath("$.followerCount", is(1)));
	}

	@Test
	void toggleFollowReturns400ForSelfFollow() throws Exception {
		User user = userWithId(1L, "alice");
		when(followService.toggleFollow(eq(1L), eq(user))).thenThrow(new SelfFollowException("cannot follow yourself"));

		mockMvc.perform(post("/api/users/{userId}/follow", 1L).with(authentication(authOf(user))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void toggleFollowReturns404ForUnknownUser() throws Exception {
		User follower = userWithId(1L, "alice");
		when(followService.toggleFollow(eq(999L), eq(follower))).thenThrow(new UserNotFoundException("user not found: 999"));

		mockMvc.perform(post("/api/users/{userId}/follow", 999L).with(authentication(authOf(follower))))
				.andExpect(status().isNotFound());
	}

	@Test
	void getFollowingReturnsPagedContent() throws Exception {
		User viewer = userWithId(1L, "alice");
		FollowUserResponse entry = new FollowUserResponse(2L, "bob", "Bob", null, true);
		var pageable = PageRequest.of(0, 20);
		when(followService.getFollowing(eq(1L), eq(1L), any())).thenReturn(new PageImpl<>(List.of(entry), pageable, 1));

		mockMvc.perform(get("/api/users/{userId}/following", 1L).with(authentication(authOf(viewer))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].userId", is(2)))
				.andExpect(jsonPath("$.content[0].followedByMe", is(true)));
	}

	@Test
	void getFollowersReturnsPagedContent() throws Exception {
		User viewer = userWithId(1L, "alice");
		FollowUserResponse entry = new FollowUserResponse(3L, "carol", "Carol", null, false);
		var pageable = PageRequest.of(0, 20);
		when(followService.getFollowers(eq(1L), eq(1L), any())).thenReturn(new PageImpl<>(List.of(entry), pageable, 1));

		mockMvc.perform(get("/api/users/{userId}/followers", 1L).with(authentication(authOf(viewer))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].userId", is(3)))
				.andExpect(jsonPath("$.content[0].followedByMe", is(false)));
	}

	@Test
	void getFollowingReturns404ForUnknownUser() throws Exception {
		User viewer = userWithId(1L, "alice");
		when(followService.getFollowing(eq(999L), eq(1L), any())).thenThrow(new UserNotFoundException("user not found: 999"));

		mockMvc.perform(get("/api/users/{userId}/following", 999L).with(authentication(authOf(viewer))))
				.andExpect(status().isNotFound());
	}
}
