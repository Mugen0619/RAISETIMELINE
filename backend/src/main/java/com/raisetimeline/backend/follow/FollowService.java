package com.raisetimeline.backend.follow;

import com.raisetimeline.backend.user.User;
import com.raisetimeline.backend.user.UserNotFoundException;
import com.raisetimeline.backend.user.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FollowService {

	private final FollowRepository followRepository;
	private final UserRepository userRepository;
	private final TransactionTemplate requiresNewTransactionTemplate;

	public FollowService(FollowRepository followRepository, UserRepository userRepository,
			PlatformTransactionManager transactionManager) {
		this.followRepository = followRepository;
		this.userRepository = userRepository;
		this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
		this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
	}

	@Transactional
	public FollowResponse toggleFollow(Long followeeId, User currentUser) {
		if (followeeId.equals(currentUser.getId())) {
			throw new SelfFollowException("cannot follow yourself");
		}

		User followee = userRepository.findById(followeeId)
				.orElseThrow(() -> new UserNotFoundException("user not found: " + followeeId));

		Optional<Follow> existing = followRepository.findByFollowerIdAndFolloweeId(currentUser.getId(), followeeId);
		boolean following;
		if (existing.isPresent()) {
			followRepository.delete(existing.get());
			following = false;
		} else {
			following = true;
			createFollowIgnoringConcurrentDuplicate(currentUser, followee);
		}

		long followerCount = followRepository.countByFolloweeId(followeeId);
		return new FollowResponse(followeeId, following, followerCount);
	}

	/**
	 * 別トランザクション(REQUIRES_NEW)でFollowの作成を試みる。同一ユーザーからの同時リクエストで
	 * ユニーク制約違反が起きても、その失敗を別トランザクションに閉じ込めることで、
	 * 呼び出し元のトランザクション(この後のcountByFolloweeId等)を巻き込まずに済む。
	 * PostgreSQLは文エラーが起きたトランザクション全体を中断状態にするため、
	 * 同一トランザクション内でtry-catchするだけでは後続のクエリが失敗してしまう。
	 */
	private void createFollowIgnoringConcurrentDuplicate(User follower, User followee) {
		try {
			requiresNewTransactionTemplate.executeWithoutResult(
					status -> followRepository.saveAndFlush(new Follow(follower, followee)));
		} catch (DataIntegrityViolationException e) {
			// 同一ユーザーからの同時リクエストで既にFollowが作成済み。トグルは冪等に成功したものとして扱う。
		}
	}

	@Transactional(readOnly = true)
	public Page<FollowUserResponse> getFollowing(Long userId, Long viewerId, Pageable pageable) {
		requireUserExists(userId);
		Page<User> followees = followRepository.findByFollowerId(userId, pageable).map(Follow::getFollowee);
		return mapWithFollowedByMe(followees, viewerId);
	}

	@Transactional(readOnly = true)
	public Page<FollowUserResponse> getFollowers(Long userId, Long viewerId, Pageable pageable) {
		requireUserExists(userId);
		Page<User> followers = followRepository.findByFolloweeId(userId, pageable).map(Follow::getFollower);
		return mapWithFollowedByMe(followers, viewerId);
	}

	private void requireUserExists(Long userId) {
		if (!userRepository.existsById(userId)) {
			throw new UserNotFoundException("user not found: " + userId);
		}
	}

	private Page<FollowUserResponse> mapWithFollowedByMe(Page<User> users, Long viewerId) {
		List<Long> userIds = users.getContent().stream().map(User::getId).toList();

		if (userIds.isEmpty()) {
			return users.map(user -> FollowUserResponse.from(user, false));
		}

		Set<Long> followedIds = new HashSet<>(followRepository.findFollowedUserIds(viewerId, userIds));
		return users.map(user -> FollowUserResponse.from(user, followedIds.contains(user.getId())));
	}
}
