package com.raisetimeline.backend.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.raisetimeline.backend.follow.FollowRepository;
import com.raisetimeline.backend.user.User;
import com.raisetimeline.backend.user.UserNotFoundException;
import com.raisetimeline.backend.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private FollowRepository followRepository;

	private ProfileService profileService;

	@BeforeEach
	void setUp() {
		profileService = new ProfileService(userRepository, followRepository);
	}

	private static User userWithId(long id, String username) {
		User user = new User(username, username + "@example.com", "hashed", username);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	@Test
	void getProfileReturnsCountsAndFollowedByMeForOtherViewer() {
		User target = userWithId(2L, "bob");
		when(userRepository.findById(2L)).thenReturn(Optional.of(target));
		when(followRepository.countByFolloweeId(2L)).thenReturn(3L);
		when(followRepository.countByFollowerId(2L)).thenReturn(5L);
		when(followRepository.existsByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(true);

		ProfileResponse response = profileService.getProfile(2L, 1L);

		assertThat(response.userId()).isEqualTo(2L);
		assertThat(response.followerCount()).isEqualTo(3L);
		assertThat(response.followingCount()).isEqualTo(5L);
		assertThat(response.followedByMe()).isTrue();
	}

	@Test
	void getProfileNeverFollowedByMeForOwnProfile() {
		User self = userWithId(1L, "alice");
		when(userRepository.findById(1L)).thenReturn(Optional.of(self));
		when(followRepository.countByFolloweeId(1L)).thenReturn(0L);
		when(followRepository.countByFollowerId(1L)).thenReturn(0L);

		ProfileResponse response = profileService.getProfile(1L, 1L);

		assertThat(response.followedByMe()).isFalse();
	}

	@Test
	void getProfileThrowsNotFoundForUnknownUser() {
		when(userRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> profileService.getProfile(999L, 1L)).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void updateProfileChangesDisplayNameAndBioForOwner() {
		User self = userWithId(1L, "alice");
		when(userRepository.findById(1L)).thenReturn(Optional.of(self));
		when(followRepository.countByFolloweeId(1L)).thenReturn(0L);
		when(followRepository.countByFollowerId(1L)).thenReturn(0L);

		ProfileResponse response = profileService.updateProfile(1L, self, new ProfileUpdateRequest("New Name", "new bio"));

		assertThat(response.displayName()).isEqualTo("New Name");
		assertThat(response.bio()).isEqualTo("new bio");
		assertThat(self.getDisplayName()).isEqualTo("New Name");
		assertThat(self.getBio()).isEqualTo("new bio");
	}

	@Test
	void updateProfileThrowsForbiddenWhenNotOwner() {
		User currentUser = userWithId(2L, "bob");

		assertThatThrownBy(() -> profileService.updateProfile(1L, currentUser, new ProfileUpdateRequest("Hacked", "bio")))
				.isInstanceOf(ForbiddenProfileAccessException.class);
	}

	@Test
	void updateProfileThrowsNotFoundForUnknownUser() {
		User self = userWithId(999L, "ghost");
		when(userRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> profileService.updateProfile(999L, self, new ProfileUpdateRequest("Name", "bio")))
				.isInstanceOf(UserNotFoundException.class);
	}
}
