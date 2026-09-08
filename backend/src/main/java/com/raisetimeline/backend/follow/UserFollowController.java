package com.raisetimeline.backend.follow;

import com.raisetimeline.backend.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}")
public class UserFollowController {

	private final FollowService followService;

	public UserFollowController(FollowService followService) {
		this.followService = followService;
	}

	@PostMapping("/follow")
	public FollowResponse toggleFollow(
			@PathVariable Long userId,
			@AuthenticationPrincipal User currentUser) {
		return followService.toggleFollow(userId, currentUser);
	}

	@GetMapping("/following")
	public PagedModel<FollowUserResponse> getFollowing(
			@PathVariable Long userId,
			@AuthenticationPrincipal User currentUser,
			@PageableDefault(size = 20, sort = { "createdAt", "id" }, direction = Sort.Direction.DESC) Pageable pageable) {
		return new PagedModel<>(followService.getFollowing(userId, currentUser.getId(), pageable));
	}

	@GetMapping("/followers")
	public PagedModel<FollowUserResponse> getFollowers(
			@PathVariable Long userId,
			@AuthenticationPrincipal User currentUser,
			@PageableDefault(size = 20, sort = { "createdAt", "id" }, direction = Sort.Direction.DESC) Pageable pageable) {
		return new PagedModel<>(followService.getFollowers(userId, currentUser.getId(), pageable));
	}
}
