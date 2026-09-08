package com.raisetimeline.backend.post;

import com.raisetimeline.backend.comment.CommentRepository;
import com.raisetimeline.backend.follow.FollowRepository;
import com.raisetimeline.backend.like.LikeRepository;
import com.raisetimeline.backend.user.User;
import com.raisetimeline.backend.user.UserNotFoundException;
import com.raisetimeline.backend.user.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

	private final PostRepository postRepository;
	private final CommentRepository commentRepository;
	private final LikeRepository likeRepository;
	private final UserRepository userRepository;
	private final FollowRepository followRepository;

	public PostService(PostRepository postRepository, CommentRepository commentRepository,
			LikeRepository likeRepository, UserRepository userRepository, FollowRepository followRepository) {
		this.postRepository = postRepository;
		this.commentRepository = commentRepository;
		this.likeRepository = likeRepository;
		this.userRepository = userRepository;
		this.followRepository = followRepository;
	}

	@Transactional
	public PostResponse createPost(User author, PostRequest request) {
		Post post = new Post(author, request.body());
		Post saved = postRepository.save(post);
		return PostResponse.from(saved, 0, 0, false);
	}

	@Transactional(readOnly = true)
	public Page<PostResponse> getTimeline(Pageable pageable, Long currentUserId) {
		return mapWithCounts(postRepository.findAll(pageable), currentUserId);
	}

	@Transactional(readOnly = true)
	public Page<PostResponse> getFollowingTimeline(Long currentUserId, Pageable pageable) {
		List<Long> followeeIds = followRepository.findFolloweeIdsByFollowerId(currentUserId);

		if (followeeIds.isEmpty()) {
			return Page.empty(pageable);
		}

		return mapWithCounts(postRepository.findByUserIdIn(followeeIds, pageable), currentUserId);
	}

	@Transactional(readOnly = true)
	public Page<PostResponse> getPostsByUser(Long profileUserId, Pageable pageable, Long currentUserId) {
		if (!userRepository.existsById(profileUserId)) {
			throw new UserNotFoundException("user not found: " + profileUserId);
		}

		return mapWithCounts(postRepository.findByUserId(profileUserId, pageable), currentUserId);
	}

	private Page<PostResponse> mapWithCounts(Page<Post> page, Long currentUserId) {
		List<Long> postIds = page.getContent().stream().map(Post::getId).toList();

		if (postIds.isEmpty()) {
			return page.map(post -> PostResponse.from(post, 0, 0, false));
		}

		Map<Long, Long> commentCounts = toCountMap(commentRepository.countGroupedByPostIds(postIds));
		Map<Long, Long> likeCounts = toCountMap(likeRepository.countGroupedByPostIds(postIds));
		Set<Long> likedPostIds = new HashSet<>(likeRepository.findLikedPostIds(currentUserId, postIds));

		return page.map(post -> PostResponse.from(
				post,
				commentCounts.getOrDefault(post.getId(), 0L),
				likeCounts.getOrDefault(post.getId(), 0L),
				likedPostIds.contains(post.getId())));
	}

	@Transactional(readOnly = true)
	public PostResponse getPost(Long postId, Long currentUserId) {
		Post post = findPost(postId);
		return PostResponse.from(post,
				commentRepository.countByPostId(postId),
				likeRepository.countByPostId(postId),
				likeRepository.existsByPostIdAndUserId(postId, currentUserId));
	}

	@Transactional
	public PostResponse updatePost(Long postId, User currentUser, PostRequest request) {
		Post post = findOwnedPost(postId, currentUser);
		post.updateBody(request.body());
		return PostResponse.from(post,
				commentRepository.countByPostId(postId),
				likeRepository.countByPostId(postId),
				likeRepository.existsByPostIdAndUserId(postId, currentUser.getId()));
	}

	@Transactional
	public void deletePost(Long postId, User currentUser) {
		Post post = findOwnedPost(postId, currentUser);
		likeRepository.deleteByPostId(postId);
		commentRepository.deleteByPostId(postId);
		postRepository.delete(post);
	}

	private Post findPost(Long postId) {
		return postRepository.findById(postId)
				.orElseThrow(() -> new PostNotFoundException("post not found: " + postId));
	}

	private Post findOwnedPost(Long postId, User currentUser) {
		Post post = findPost(postId);

		if (!post.getUser().getId().equals(currentUser.getId())) {
			throw new ForbiddenPostAccessException("only the author can modify this post");
		}

		return post;
	}

	private static Map<Long, Long> toCountMap(List<PostCountProjection> projections) {
		return projections.stream()
				.collect(Collectors.toMap(PostCountProjection::getPostId, PostCountProjection::getCount));
	}
}
