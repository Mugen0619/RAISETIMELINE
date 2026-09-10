package com.raisetimeline.backend.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserSearchController {

	private final UserSearchService userSearchService;

	public UserSearchController(UserSearchService userSearchService) {
		this.userSearchService = userSearchService;
	}

	@GetMapping("/search")
	public PagedModel<UserSummaryResponse> search(
			@RequestParam(required = false) String q,
			@PageableDefault(size = 20, sort = { "createdAt", "id" }, direction = Sort.Direction.DESC) Pageable pageable) {
		return new PagedModel<>(userSearchService.search(q, pageable));
	}
}
