package com.raisetimeline.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserSearchServiceTest {

	@Mock
	private UserRepository userRepository;

	private UserSearchService userSearchService;

	@BeforeEach
	void setUp() {
		userSearchService = new UserSearchService(userRepository);
	}

	private static User userWithId(long id, String username, String displayName) {
		User user = new User(username, username + "@example.com", "hashed", displayName);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	@Test
	void searchDelegatesToRepositoryWithSameKeywordForBothFields() {
		User alice = userWithId(1L, "alice", "Alice A.");
		Pageable pageable = PageRequest.of(0, 20);
		when(userRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase("ali", "ali", pageable))
				.thenReturn(new PageImpl<>(List.of(alice), pageable, 1));

		Page<UserSummaryResponse> result = userSearchService.search("ali", pageable);

		assertThat(result.getTotalElements()).isEqualTo(1);
		assertThat(result.getContent().get(0).username()).isEqualTo("alice");
	}

	@Test
	void searchTrimsWhitespaceFromQuery() {
		Pageable pageable = PageRequest.of(0, 20);
		when(userRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase("ali", "ali", pageable))
				.thenReturn(Page.empty(pageable));

		userSearchService.search("  ali  ", pageable);

		verify(userRepository).findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase("ali", "ali", pageable);
	}

	@Test
	void searchReturnsEmptyPageWithoutQueryingRepositoryForBlankQuery() {
		Pageable pageable = PageRequest.of(0, 20);

		Page<UserSummaryResponse> result = userSearchService.search("   ", pageable);

		assertThat(result.getTotalElements()).isZero();
		verify(userRepository, never())
				.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(any(), any(), any());
	}

	@Test
	void searchReturnsEmptyPageWithoutQueryingRepositoryForNullQuery() {
		Pageable pageable = PageRequest.of(0, 20);

		Page<UserSummaryResponse> result = userSearchService.search(null, pageable);

		assertThat(result.getTotalElements()).isZero();
		verify(userRepository, never())
				.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(any(), any(), any());
	}

	@Test
	void searchReturnsEmptyPageWhenRepositoryFindsNoMatches() {
		Pageable pageable = PageRequest.of(0, 20);
		when(userRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase("zzz", "zzz", pageable))
				.thenReturn(Page.empty(pageable));

		Page<UserSummaryResponse> result = userSearchService.search("zzz", pageable);

		assertThat(result.getTotalElements()).isZero();
		assertThat(result.getContent()).isEmpty();
	}

	@Test
	void searchPassesSqlInjectionAttemptThroughAsAnOrdinaryParameterValue() {
		String maliciousQuery = "'; DROP TABLE users; --";
		Pageable pageable = PageRequest.of(0, 20);
		when(userRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(maliciousQuery, maliciousQuery, pageable))
				.thenReturn(Page.empty(pageable));

		Page<UserSummaryResponse> result = userSearchService.search(maliciousQuery, pageable);

		assertThat(result.getTotalElements()).isZero();
		verify(userRepository).findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(maliciousQuery, maliciousQuery, pageable);
	}
}
