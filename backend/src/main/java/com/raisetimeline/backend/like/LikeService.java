package com.raisetimeline.backend.like;

import com.raisetimeline.backend.post.Post;
import com.raisetimeline.backend.post.PostNotFoundException;
import com.raisetimeline.backend.post.PostRepository;
import com.raisetimeline.backend.user.User;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LikeService {

	private final LikeRepository likeRepository;
	private final PostRepository postRepository;
	private final TransactionTemplate requiresNewTransactionTemplate;

	public LikeService(LikeRepository likeRepository, PostRepository postRepository,
			PlatformTransactionManager transactionManager) {
		this.likeRepository = likeRepository;
		this.postRepository = postRepository;
		this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
		this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
	}

	@Transactional
	public LikeResponse toggleLike(Long postId, User currentUser) {
		Post post = postRepository.findById(postId)
				.orElseThrow(() -> new PostNotFoundException("post not found: " + postId));

		Optional<Like> existing = likeRepository.findByPostIdAndUserId(postId, currentUser.getId());
		boolean liked;
		if (existing.isPresent()) {
			likeRepository.delete(existing.get());
			liked = false;
		} else {
			liked = true;
			createLikeIgnoringConcurrentDuplicate(post, currentUser);
		}

		long likeCount = likeRepository.countByPostId(postId);
		return new LikeResponse(postId, liked, likeCount);
	}

	/**
	 * 別トランザクション(REQUIRES_NEW)でLikeの作成を試みる。同一ユーザーからの同時リクエストで
	 * ユニーク制約違反が起きても、その失敗を別トランザクションに閉じ込めることで、
	 * 呼び出し元のトランザクション(この後のcountByPostId等)を巻き込まずに済む。
	 * PostgreSQLは文エラーが起きたトランザクション全体を中断状態にするため、
	 * 同一トランザクション内でtry-catchするだけでは後続のクエリが失敗してしまう。
	 */
	private void createLikeIgnoringConcurrentDuplicate(Post post, User user) {
		try {
			requiresNewTransactionTemplate.executeWithoutResult(status -> likeRepository.saveAndFlush(new Like(post, user)));
		} catch (DataIntegrityViolationException e) {
			// 同一ユーザーからの同時リクエストで既にLikeが作成済み。トグルは冪等に成功したものとして扱う。
		}
	}
}
