package com.raisetimeline.backend.post;

import com.raisetimeline.backend.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/timeline")
public class TimelineController {

	private final PostService postService;

	public TimelineController(PostService postService) {
		this.postService = postService;
	}

	@GetMapping("/following")
	public PagedModel<PostResponse> getFollowingTimeline(
			@AuthenticationPrincipal User currentUser,
			@PageableDefault(size = 20, sort = { "createdAt", "id" }, direction = Sort.Direction.DESC) Pageable pageable) {
		return new PagedModel<>(postService.getFollowingTimeline(currentUser.getId(), pageable));
	}
}
