package com.raisetimeline.backend.post;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * TimelineControllerのWeb層テスト。PostServiceはMockitoでモックし、
 * HTTPステータス・レスポンス形式のみを検証する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TimelineControllerTest {

	@Autowired
	private MockMvc mockMvc;

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
	void getFollowingTimelineReturnsPagedContent() throws Exception {
		User viewer = userWithId(1L, "alice");
		Instant now = Instant.now();
		PostResponse response = new PostResponse(100L, 2L, "bob", "Bob", "hello", now, now, 0, 0, false, List.of());
		var pageable = PageRequest.of(0, 20);
		when(postService.getFollowingTimeline(eq(1L), any())).thenReturn(new PageImpl<>(List.of(response), pageable, 1));

		mockMvc.perform(get("/api/timeline/following").with(authentication(authOf(viewer))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id", is(100)))
				.andExpect(jsonPath("$.content[0].username", is("bob")))
				.andExpect(jsonPath("$.page.totalElements", is(1)));
	}

	@Test
	void getFollowingTimelineReturns200WithEmptyContentWhenNotFollowingAnyone() throws Exception {
		User viewer = userWithId(1L, "alice");
		var pageable = PageRequest.of(0, 20);
		when(postService.getFollowingTimeline(eq(1L), any())).thenReturn(new PageImpl<>(List.of(), pageable, 0));

		mockMvc.perform(get("/api/timeline/following").with(authentication(authOf(viewer))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()", is(0)));
	}

	@Test
	void getFollowingTimelineReturns401WithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/timeline/following"))
				.andExpect(status().isUnauthorized());
	}
}
