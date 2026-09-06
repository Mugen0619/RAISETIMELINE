package com.raisetimeline.backend.like;

import com.raisetimeline.backend.post.Post;
import com.raisetimeline.backend.post.PostNotFoundException;
import com.raisetimeline.backend.post.PostRepository;
import com.raisetimeline.backend.user.User;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LikeService {

	private final LikeRepository likeRepository;
	private final PostRepository postRepository;

	public LikeService(LikeRepository likeRepository, PostRepository postRepository) {
		this.likeRepository = likeRepository;
		this.postRepository = postRepository;
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
			try {
				likeRepository.saveAndFlush(new Like(post, currentUser));
			} catch (DataIntegrityViolationException e) {
				// 同一ユーザーからの同時リクエストで既にLikeが作成済み。トグルは冪等に成功したものとして扱う。
			}
		}

		long likeCount = likeRepository.countByPostId(postId);
		return new LikeResponse(postId, liked, likeCount);
	}
}
