package com.raisetimeline.backend.profile;

import com.raisetimeline.backend.user.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}")
public class ProfileController {

	private final ProfileService profileService;

	public ProfileController(ProfileService profileService) {
		this.profileService = profileService;
	}

	@GetMapping
	public ProfileResponse getProfile(
			@PathVariable Long userId,
			@AuthenticationPrincipal User currentUser) {
		return profileService.getProfile(userId, currentUser.getId());
	}

	@PutMapping
	public ProfileResponse updateProfile(
			@PathVariable Long userId,
			@AuthenticationPrincipal User currentUser,
			@Valid @RequestBody ProfileUpdateRequest request) {
		return profileService.updateProfile(userId, currentUser, request);
	}
}
