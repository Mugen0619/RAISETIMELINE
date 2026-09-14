package com.raisetimeline.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raisetimeline.backend.logging.MdcKeys;
import com.raisetimeline.backend.user.User;
import com.raisetimeline.backend.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

	@Mock
	private JwtService jwtService;

	@Mock
	private UserRepository userRepository;

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpServletResponse response;

	@Mock
	private FilterChain filterChain;

	private JwtAuthenticationFilter filter;

	@BeforeEach
	void setUp() {
		filter = new JwtAuthenticationFilter(jwtService, userRepository);
	}

	private static User userWithId(long id, String username) {
		User user = new User(username, username + "@example.com", "hashed", username);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
		MDC.clear();
	}

	@Test
	void putsUserIdInMdcWhenTokenIsValid() throws Exception {
		when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
		when(jwtService.isValid("valid-token")).thenReturn(true);
		when(jwtService.extractUserId("valid-token")).thenReturn(42L);
		when(userRepository.findById(42L)).thenReturn(Optional.of(userWithId(42L, "alice")));

		filter.doFilter(request, response, filterChain);

		assertThat(MDC.get(MdcKeys.USER_ID)).isEqualTo("42");
		verify(filterChain).doFilter(request, response);
	}

	@Test
	void doesNotPutUserIdInMdcWhenNoAuthorizationHeader() throws Exception {
		when(request.getHeader("Authorization")).thenReturn(null);

		filter.doFilter(request, response, filterChain);

		assertThat(MDC.get(MdcKeys.USER_ID)).isNull();
	}

	@Test
	void doesNotPutUserIdInMdcWhenTokenIsInvalid() throws Exception {
		when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
		when(jwtService.isValid("invalid-token")).thenReturn(false);

		filter.doFilter(request, response, filterChain);

		assertThat(MDC.get(MdcKeys.USER_ID)).isNull();
	}

	@Test
	void doesNotPutUserIdInMdcWhenUserNoLongerExists() throws Exception {
		when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
		when(jwtService.isValid("valid-token")).thenReturn(true);
		when(jwtService.extractUserId("valid-token")).thenReturn(999L);
		when(userRepository.findById(999L)).thenReturn(Optional.empty());

		filter.doFilter(request, response, filterChain);

		assertThat(MDC.get(MdcKeys.USER_ID)).isNull();
	}
}
