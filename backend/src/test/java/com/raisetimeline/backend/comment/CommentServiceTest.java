package com.raisetimeline.backend.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raisetimeline.backend.post.Post;
import com.raisetimeline.backend.post.PostNotFoundException;
import com.raisetimeline.backend.post.PostRepository;
import com.raisetimeline.backend.user.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

	@Mock
	private CommentRepository commentRepository;

	@Mock
	private PostRepository postRepository;

	private CommentService commentService;

	@BeforeEach
	void setUp() {
		commentService = new CommentService(commentRepository, postRepository);
	}

	private static User userWithId(long id, String username) {
		User user = new User(username, username + "@example.com", "hashed", username);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private static Post postWithId(long id, User author) {
		Post post = new Post(author, "some post");
		ReflectionTestUtils.setField(post, "id", id);
		return post;
	}

	@Test
	void createCommentSavesCommentAuthoredByCurrentUserOnExistingPost() {
		User postAuthor = userWithId(1L, "alice");
		Post post = postWithId(100L, postAuthor);
		User commenter = userWithId(2L, "bob");
		when(postRepository.findById(100L)).thenReturn(Optional.of(post));
		when(commentRepository.save(org.mockito.ArgumentMatchers.any(Comment.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		CommentResponse response = commentService.createComment(100L, commenter, new CommentRequest("nice post"));

		ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
		verify(commentRepository).save(captor.capture());
		assertThat(captor.getValue().getPost()).isSameAs(post);
		assertThat(captor.getValue().getUser()).isSameAs(commenter);
		assertThat(captor.getValue().getBody()).isEqualTo("nice post");
		assertThat(response.postId()).isEqualTo(100L);
		assertThat(response.userId()).isEqualTo(2L);
		assertThat(response.username()).isEqualTo("bob");
		assertThat(response.body()).isEqualTo("nice post");
	}

	@Test
	void createCommentAllowsCommentingOnOwnPost() {
		User author = userWithId(1L, "alice");
		Post post = postWithId(100L, author);
		when(postRepository.findById(100L)).thenReturn(Optional.of(post));
		when(commentRepository.save(org.mockito.ArgumentMatchers.any(Comment.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		CommentResponse response = commentService.createComment(100L, author, new CommentRequest("self comment"));

		assertThat(response.userId()).isEqualTo(1L);
	}

	@Test
	void createCommentThrowsNotFoundForUnknownPost() {
		User commenter = userWithId(2L, "bob");
		when(postRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> commentService.createComment(999L, commenter, new CommentRequest("body")))
				.isInstanceOf(PostNotFoundException.class);

		verify(commentRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void getCommentsReturnsPageOfCommentsForExistingPost() {
		User author = userWithId(1L, "alice");
		Post post = postWithId(100L, author);
		Comment comment = new Comment(post, author, "first comment");
		ReflectionTestUtils.setField(comment, "id", 500L);
		Pageable pageable = PageRequest.of(0, 20);
		when(postRepository.existsById(100L)).thenReturn(true);
		when(commentRepository.findByPostId(100L, pageable)).thenReturn(new PageImpl<>(List.of(comment), pageable, 1));

		var page = commentService.getComments(100L, pageable);

		assertThat(page.getTotalElements()).isEqualTo(1);
		assertThat(page.getContent().get(0).body()).isEqualTo("first comment");
	}

	@Test
	void getCommentsThrowsNotFoundForUnknownPost() {
		Pageable pageable = PageRequest.of(0, 20);
		when(postRepository.existsById(999L)).thenReturn(false);

		assertThatThrownBy(() -> commentService.getComments(999L, pageable)).isInstanceOf(PostNotFoundException.class);
	}

	@Test
	void deleteCommentRemovesCommentWhenCurrentUserIsAuthor() {
		User author = userWithId(1L, "alice");
		Post post = postWithId(100L, author);
		Comment comment = new Comment(post, author, "my comment");
		ReflectionTestUtils.setField(comment, "id", 500L);
		when(commentRepository.findById(500L)).thenReturn(Optional.of(comment));

		commentService.deleteComment(500L, author);

		verify(commentRepository).delete(comment);
	}

	@Test
	void deleteCommentThrowsForbiddenWhenCurrentUserIsNotAuthor() {
		User author = userWithId(1L, "alice");
		User otherUser = userWithId(2L, "bob");
		Post post = postWithId(100L, author);
		Comment comment = new Comment(post, author, "my comment");
		ReflectionTestUtils.setField(comment, "id", 500L);
		when(commentRepository.findById(500L)).thenReturn(Optional.of(comment));

		assertThatThrownBy(() -> commentService.deleteComment(500L, otherUser))
				.isInstanceOf(ForbiddenCommentAccessException.class);

		verify(commentRepository, never()).delete(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void deleteCommentThrowsNotFoundForUnknownComment() {
		User author = userWithId(1L, "alice");
		when(commentRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> commentService.deleteComment(999L, author))
				.isInstanceOf(CommentNotFoundException.class);
	}
}
