package com.raisetimeline.backend.follow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raisetimeline.backend.user.User;
import com.raisetimeline.backend.user.UserNotFoundException;
import com.raisetimeline.backend.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

	/**
	 * REQUIRES_NEWでの実行を検証するための最小限のPlatformTransactionManager。
	 * 実DBを使わない単体テストでは、TransactionTemplateがコールバックを実行し、
	 * 例外発生時はそのまま呼び出し元に伝播することさえ確認できればよい。
	 */
	private static final PlatformTransactionManager NOOP_TRANSACTION_MANAGER = new PlatformTransactionManager() {
		@Override
		public TransactionStatus getTransaction(TransactionDefinition definition) {
			return new SimpleTransactionStatus();
		}

		@Override
		public void commit(TransactionStatus status) {
		}

		@Override
		public void rollback(TransactionStatus status) {
		}
	};

	@Mock
	private FollowRepository followRepository;

	@Mock
	private UserRepository userRepository;

	private FollowService followService;

	@BeforeEach
	void setUp() {
		followService = new FollowService(followRepository, userRepository, NOOP_TRANSACTION_MANAGER);
	}

	private static User userWithId(long id, String username) {
		User user = new User(username, username + "@example.com", "hashed", username);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	@Test
	void toggleFollowCreatesFollowWhenNotAlreadyFollowing() {
		User follower = userWithId(1L, "alice");
		User followee = userWithId(2L, "bob");
		when(userRepository.findById(2L)).thenReturn(Optional.of(followee));
		when(followRepository.findByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(Optional.empty());
		when(followRepository.countByFolloweeId(2L)).thenReturn(1L);

		FollowResponse response = followService.toggleFollow(2L, follower);

		ArgumentCaptor<Follow> captor = ArgumentCaptor.forClass(Follow.class);
		verify(followRepository).saveAndFlush(captor.capture());
		assertThat(captor.getValue().getFollower()).isSameAs(follower);
		assertThat(captor.getValue().getFollowee()).isSameAs(followee);
		assertThat(response.following()).isTrue();
		assertThat(response.followerCount()).isEqualTo(1L);
		verify(followRepository, never()).delete(any());
	}

	@Test
	void toggleFollowRemovesFollowWhenAlreadyFollowing() {
		User follower = userWithId(1L, "alice");
		User followee = userWithId(2L, "bob");
		Follow existing = new Follow(follower, followee);
		when(userRepository.findById(2L)).thenReturn(Optional.of(followee));
		when(followRepository.findByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(Optional.of(existing));
		when(followRepository.countByFolloweeId(2L)).thenReturn(0L);

		FollowResponse response = followService.toggleFollow(2L, follower);

		verify(followRepository).delete(existing);
		verify(followRepository, never()).saveAndFlush(any());
		assertThat(response.following()).isFalse();
		assertThat(response.followerCount()).isEqualTo(0L);
	}

	@Test
	void toggleFollowThrowsSelfFollowExceptionForOwnId() {
		User user = userWithId(1L, "alice");

		assertThatThrownBy(() -> followService.toggleFollow(1L, user)).isInstanceOf(SelfFollowException.class);

		verify(userRepository, never()).findById(any());
		verify(followRepository, never()).saveAndFlush(any());
	}

	@Test
	void toggleFollowThrowsNotFoundForUnknownUser() {
		User follower = userWithId(1L, "alice");
		when(userRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> followService.toggleFollow(999L, follower)).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void toggleFollowTreatsConcurrentDuplicateInsertAsIdempotentSuccess() {
		User follower = userWithId(1L, "alice");
		User followee = userWithId(2L, "bob");
		when(userRepository.findById(2L)).thenReturn(Optional.of(followee));
		when(followRepository.findByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(Optional.empty());
		when(followRepository.saveAndFlush(any(Follow.class)))
				.thenThrow(new DataIntegrityViolationException("uk_follows_follower_followee violated"));
		when(followRepository.countByFolloweeId(2L)).thenReturn(1L);

		FollowResponse response = followService.toggleFollow(2L, follower);

		assertThat(response.following()).isTrue();
		assertThat(response.followerCount()).isEqualTo(1L);
	}

	@Test
	void getFollowingReturnsFolloweesWithBulkFollowedByMeCheck() {
		User target = userWithId(2L, "bob");
		Follow follow = new Follow(userWithId(1L, "alice"), target);
		Pageable pageable = PageRequest.of(0, 20);
		when(userRepository.existsById(1L)).thenReturn(true);
		when(followRepository.findByFollowerId(1L, pageable)).thenReturn(new PageImpl<>(List.of(follow), pageable, 1));
		when(followRepository.findFollowedUserIds(eq(9L), eq(List.of(2L)))).thenReturn(List.of(2L));

		var page = followService.getFollowing(1L, 9L, pageable);

		assertThat(page.getTotalElements()).isEqualTo(1);
		assertThat(page.getContent().get(0).userId()).isEqualTo(2L);
		assertThat(page.getContent().get(0).followedByMe()).isTrue();
	}

	@Test
	void getFollowingSkipsBatchQueryWhenPageIsEmpty() {
		Pageable pageable = PageRequest.of(0, 20);
		when(userRepository.existsById(1L)).thenReturn(true);
		when(followRepository.findByFollowerId(1L, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

		var page = followService.getFollowing(1L, 9L, pageable);

		assertThat(page.getTotalElements()).isZero();
		verify(followRepository, never()).findFollowedUserIds(any(), any());
	}

	@Test
	void getFollowingThrowsNotFoundForUnknownUser() {
		Pageable pageable = PageRequest.of(0, 20);
		when(userRepository.existsById(999L)).thenReturn(false);

		assertThatThrownBy(() -> followService.getFollowing(999L, 1L, pageable)).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void getFollowersReturnsFollowersWithBulkFollowedByMeCheck() {
		User target = userWithId(2L, "bob");
		Follow follow = new Follow(target, userWithId(1L, "alice"));
		Pageable pageable = PageRequest.of(0, 20);
		when(userRepository.existsById(1L)).thenReturn(true);
		when(followRepository.findByFolloweeId(1L, pageable)).thenReturn(new PageImpl<>(List.of(follow), pageable, 1));
		when(followRepository.findFollowedUserIds(eq(9L), eq(List.of(2L)))).thenReturn(List.of());

		var page = followService.getFollowers(1L, 9L, pageable);

		assertThat(page.getTotalElements()).isEqualTo(1);
		assertThat(page.getContent().get(0).userId()).isEqualTo(2L);
		assertThat(page.getContent().get(0).followedByMe()).isFalse();
	}
}
