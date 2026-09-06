package com.raisetimeline.backend.comment;

import com.raisetimeline.backend.post.Post;
import com.raisetimeline.backend.post.PostNotFoundException;
import com.raisetimeline.backend.post.PostRepository;
import com.raisetimeline.backend.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

	private final CommentRepository commentRepository;
	private final PostRepository postRepository;

	public CommentService(CommentRepository commentRepository, PostRepository postRepository) {
		this.commentRepository = commentRepository;
		this.postRepository = postRepository;
	}

	@Transactional
	public CommentResponse createComment(Long postId, User author, CommentRequest request) {
		Post post = postRepository.findById(postId)
				.orElseThrow(() -> new PostNotFoundException("post not found: " + postId));

		Comment comment = new Comment(post, author, request.body());
		Comment saved = commentRepository.save(comment);
		return CommentResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public Page<CommentResponse> getComments(Long postId, Pageable pageable) {
		if (!postRepository.existsById(postId)) {
			throw new PostNotFoundException("post not found: " + postId);
		}

		return commentRepository.findByPostId(postId, pageable).map(CommentResponse::from);
	}

	@Transactional
	public void deleteComment(Long commentId, User currentUser) {
		Comment comment = commentRepository.findById(commentId)
				.orElseThrow(() -> new CommentNotFoundException("comment not found: " + commentId));

		if (!comment.getUser().getId().equals(currentUser.getId())) {
			throw new ForbiddenCommentAccessException("only the author can delete this comment");
		}

		commentRepository.delete(comment);
	}
}
