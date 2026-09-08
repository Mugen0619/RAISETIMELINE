package com.raisetimeline.backend.profile;

import com.raisetimeline.backend.follow.FollowRepository;
import com.raisetimeline.backend.user.User;
import com.raisetimeline.backend.user.UserNotFoundException;
import com.raisetimeline.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

	private final UserRepository userRepository;
	private final FollowRepository followRepository;

	public ProfileService(UserRepository userRepository, FollowRepository followRepository) {
		this.userRepository = userRepository;
		this.followRepository = followRepository;
	}

	@Transactional(readOnly = true)
	public ProfileResponse getProfile(Long userId, Long viewerId) {
		User user = findUser(userId);
		boolean followedByMe = !userId.equals(viewerId)
				&& followRepository.existsByFollowerIdAndFolloweeId(viewerId, userId);

		return ProfileResponse.from(user,
				followRepository.countByFolloweeId(userId),
				followRepository.countByFollowerId(userId),
				followedByMe);
	}

	@Transactional
	public ProfileResponse updateProfile(Long userId, User currentUser, ProfileUpdateRequest request) {
		if (!userId.equals(currentUser.getId())) {
			throw new ForbiddenProfileAccessException("only the account owner can edit this profile");
		}

		User user = findUser(userId);
		user.updateProfile(request.displayName(), request.bio());

		return ProfileResponse.from(user,
				followRepository.countByFolloweeId(userId),
				followRepository.countByFollowerId(userId),
				false);
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new UserNotFoundException("user not found: " + userId));
	}
}
