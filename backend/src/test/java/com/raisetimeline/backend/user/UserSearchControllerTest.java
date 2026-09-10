package com.raisetimeline.backend.user;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UserSearchControllerのWeb層テスト。UserSearchServiceはMockitoでモックし、
 * HTTPステータス・レスポンス形状のみを検証する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserSearchControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserSearchService userSearchService;

	private static User userWithId(long id, String username) {
		User user = new User(username, username + "@example.com", "hashed", username);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private static Authentication authOf(User user) {
		return new UsernamePasswordAuthenticationToken(user, null, List.of());
	}

	@Test
	void searchReturns200WithResults() throws Exception {
		User requester = userWithId(1L, "alice");
		UserSummaryResponse result = new UserSummaryResponse(2L, "bob", "Bob", null);
		Page<UserSummaryResponse> page = new PageImpl<>(List.of(result), PageRequest.of(0, 20), 1);
		when(userSearchService.search(eq("bo"), any())).thenReturn(page);

		mockMvc.perform(get("/api/users/search").param("q", "bo").with(authentication(authOf(requester))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].username", is("bob")))
				.andExpect(jsonPath("$.page.totalElements", is(1)));
	}

	@Test
	void searchReturnsEmptyContentWhenNoMatches() throws Exception {
		User requester = userWithId(1L, "alice");
		when(userSearchService.search(eq("zzz"), any())).thenReturn(Page.empty());

		mockMvc.perform(get("/api/users/search").param("q", "zzz").with(authentication(authOf(requester))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty());
	}

	@Test
	void searchRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/users/search").param("q", "bo"))
				.andExpect(status().isUnauthorized());
	}
}
