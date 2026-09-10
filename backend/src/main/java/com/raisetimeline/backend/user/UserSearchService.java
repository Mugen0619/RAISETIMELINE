package com.raisetimeline.backend.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSearchService {

	private final UserRepository userRepository;

	public UserSearchService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public Page<UserSummaryResponse> search(String query, Pageable pageable) {
		String keyword = query == null ? "" : query.trim();

		if (keyword.isEmpty()) {
			return Page.empty(pageable);
		}

		return userRepository
				.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(keyword, keyword, pageable)
				.map(UserSummaryResponse::from);
	}
}
