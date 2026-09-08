package com.raisetimeline.backend.profile;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.backend.user.User;
import com.raisetimeline.backend.user.UserNotFoundException;
import java.time.Instant;
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
 * ProfileControllerのWeb層テスト。ProfileServiceはMockitoでモックし、
 * バリデーション・HTTPステータス・例外のマッピングのみを検証する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ProfileService profileService;

	private static User userWithId(long id, String username) {
		User user = new User(username, username + "@example.com", "hashed", username);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private static Authentication authOf(User user) {
		return new UsernamePasswordAuthenticationToken(user, null, List.of());
	}

	@Test
	void getProfileReturns200WithProfileData() throws Exception {
		User viewer = userWithId(1L, "alice");
		ProfileResponse response = new ProfileResponse(2L, "bob", "Bob", "hi", null, 3, 5, true, Instant.now());
		when(profileService.getProfile(2L, 1L)).thenReturn(response);

		mockMvc.perform(get("/api/users/{userId}", 2L).with(authentication(authOf(viewer))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username", is("bob")))
				.andExpect(jsonPath("$.followerCount", is(3)))
				.andExpect(jsonPath("$.followingCount", is(5)))
				.andExpect(jsonPath("$.followedByMe", is(true)));
	}

	@Test
	void getProfileReturns404ForUnknownUser() throws Exception {
		User viewer = userWithId(1L, "alice");
		when(profileService.getProfile(999L, 1L)).thenThrow(new UserNotFoundException("user not found: 999"));

		mockMvc.perform(get("/api/users/{userId}", 999L).with(authentication(authOf(viewer))))
				.andExpect(status().isNotFound());
	}

	@Test
	void updateProfileReturns200ForOwner() throws Exception {
		User self = userWithId(1L, "alice");
		ProfileResponse response = new ProfileResponse(1L, "alice", "New Name", "new bio", null, 0, 0, false, Instant.now());
		when(profileService.updateProfile(eq(1L), eq(self), any(ProfileUpdateRequest.class))).thenReturn(response);

		mockMvc.perform(put("/api/users/{userId}", 1L)
						.with(authentication(authOf(self)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new ProfileUpdateRequest("New Name", "new bio"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName", is("New Name")));
	}

	@Test
	void updateProfileReturns403WhenNotOwner() throws Exception {
		User otherUser = userWithId(2L, "bob");
		when(profileService.updateProfile(eq(1L), eq(otherUser), any(ProfileUpdateRequest.class)))
				.thenThrow(new ForbiddenProfileAccessException("only the account owner can edit this profile"));

		mockMvc.perform(put("/api/users/{userId}", 1L)
						.with(authentication(authOf(otherUser)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new ProfileUpdateRequest("Hacked", "bio"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void updateProfileReturns400ForBlankDisplayName() throws Exception {
		User self = userWithId(1L, "alice");

		mockMvc.perform(put("/api/users/{userId}", 1L)
						.with(authentication(authOf(self)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new ProfileUpdateRequest("", "bio"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void updateProfileReturns400ForDisplayNameOver64Characters() throws Exception {
		User self = userWithId(1L, "alice");

		mockMvc.perform(put("/api/users/{userId}", 1L)
						.with(authentication(authOf(self)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new ProfileUpdateRequest("a".repeat(65), "bio"))))
				.andExpect(status().isBadRequest());
	}
}
