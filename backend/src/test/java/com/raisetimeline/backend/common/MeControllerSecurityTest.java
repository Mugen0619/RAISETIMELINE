package com.raisetimeline.backend.common;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.backend.security.JwtService;
import com.raisetimeline.backend.user.User;
import com.raisetimeline.backend.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MeControllerSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private UserRepository userRepository;

	@Test
	void requestWithoutTokenIsRejected() throws Exception {
		mockMvc.perform(get("/api/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void requestWithInvalidTokenIsRejected() throws Exception {
		when(jwtService.isValid("invalid-token")).thenReturn(false);

		mockMvc.perform(get("/api/me").header("Authorization", "Bearer invalid-token"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void requestWithValidTokenIsAccepted() throws Exception {
		User user = new User("dave", "dave@example.com", "hashed-password", "Dave");
		ReflectionTestUtils.setField(user, "id", 1L);

		when(jwtService.isValid("valid-token")).thenReturn(true);
		when(jwtService.extractUserId("valid-token")).thenReturn(1L);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));

		mockMvc.perform(get("/api/me").header("Authorization", "Bearer valid-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username", is("dave")))
				.andExpect(jsonPath("$.id", is(1)));
	}
}
