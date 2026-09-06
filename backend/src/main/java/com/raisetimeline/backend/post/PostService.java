package com.raisetimeline.backend.post;

import com.raisetimeline.backend.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

	private final PostRepository postRepository;

	public PostService(PostRepository postRepository) {
		this.postRepository = postRepository;
	}

	@Transactional
	public PostResponse createPost(User author, PostRequest request) {
		Post post = new Post(author, request.body());
		Post saved = postRepository.save(post);
		return PostResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public Page<PostResponse> getTimeline(Pageable pageable) {
		return postRepository.findAll(pageable).map(PostResponse::from);
	}

	@Transactional
	public PostResponse updatePost(Long postId, User currentUser, PostRequest request) {
		Post post = findOwnedPost(postId, currentUser);
		post.updateBody(request.body());
		return PostResponse.from(post);
	}

	@Transactional
	public void deletePost(Long postId, User currentUser) {
		Post post = findOwnedPost(postId, currentUser);
		postRepository.delete(post);
	}

	private Post findOwnedPost(Long postId, User currentUser) {
		Post post = postRepository.findById(postId)
				.orElseThrow(() -> new PostNotFoundException("post not found: " + postId));

		if (!post.getUser().getId().equals(currentUser.getId())) {
			throw new ForbiddenPostAccessException("only the author can modify this post");
		}

		return post;
	}
}
