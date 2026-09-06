package com.raisetimeline.backend.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raisetimeline.backend.user.User;
import java.time.Instant;
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
class PostServiceTest {

	@Mock
	private PostRepository postRepository;

	private PostService postService;

	@BeforeEach
	void setUp() {
		postService = new PostService(postRepository);
	}

	private static User userWithId(long id, String username) {
		User user = new User(username, username + "@example.com", "hashed", username);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	@Test
	void createPostSavesPostAuthoredByCurrentUser() {
		User author = userWithId(1L, "alice");
		when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

		PostResponse response = postService.createPost(author, new PostRequest("hello world"));

		ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
		verify(postRepository).save(captor.capture());
		assertThat(captor.getValue().getUser()).isSameAs(author);
		assertThat(captor.getValue().getBody()).isEqualTo("hello world");
		assertThat(response.userId()).isEqualTo(1L);
		assertThat(response.username()).isEqualTo("alice");
		assertThat(response.body()).isEqualTo("hello world");
	}

	@Test
	void getTimelineMapsPageOfPostsToPostResponses() {
		User author = userWithId(1L, "alice");
		Post post = new Post(author, "hello world");
		ReflectionTestUtils.setField(post, "id", 100L);
		Pageable pageable = PageRequest.of(0, 20);
		when(postRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(post), pageable, 1));

		var page = postService.getTimeline(pageable);

		assertThat(page.getTotalElements()).isEqualTo(1);
		assertThat(page.getContent()).hasSize(1);
		assertThat(page.getContent().get(0).id()).isEqualTo(100L);
		assertThat(page.getContent().get(0).username()).isEqualTo("alice");
	}

	@Test
	void updatePostChangesBodyWhenCurrentUserIsAuthor() {
		User author = userWithId(1L, "alice");
		Post post = new Post(author, "old body");
		ReflectionTestUtils.setField(post, "id", 100L);
		when(postRepository.findById(100L)).thenReturn(Optional.of(post));

		PostResponse response = postService.updatePost(100L, author, new PostRequest("new body"));

		assertThat(response.body()).isEqualTo("new body");
		assertThat(post.getBody()).isEqualTo("new body");
	}

	@Test
	void updatePostRefreshesUpdatedAtImmediatelyInTheResponse() {
		User author = userWithId(1L, "alice");
		Post post = new Post(author, "old body");
		ReflectionTestUtils.setField(post, "id", 100L);
		Instant staleUpdatedAt = Instant.now().minusSeconds(60);
		ReflectionTestUtils.setField(post, "updatedAt", staleUpdatedAt);
		when(postRepository.findById(100L)).thenReturn(Optional.of(post));

		PostResponse response = postService.updatePost(100L, author, new PostRequest("new body"));

		assertThat(response.updatedAt()).isAfter(staleUpdatedAt);
	}

	@Test
	void updatePostThrowsForbiddenWhenCurrentUserIsNotAuthor() {
		User author = userWithId(1L, "alice");
		User otherUser = userWithId(2L, "bob");
		Post post = new Post(author, "old body");
		ReflectionTestUtils.setField(post, "id", 100L);
		when(postRepository.findById(100L)).thenReturn(Optional.of(post));

		assertThatThrownBy(() -> postService.updatePost(100L, otherUser, new PostRequest("hijacked")))
				.isInstanceOf(ForbiddenPostAccessException.class);

		assertThat(post.getBody()).isEqualTo("old body");
	}

	@Test
	void updatePostThrowsNotFoundForUnknownPost() {
		User author = userWithId(1L, "alice");
		when(postRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> postService.updatePost(999L, author, new PostRequest("new body")))
				.isInstanceOf(PostNotFoundException.class);
	}

	@Test
	void deletePostRemovesPostWhenCurrentUserIsAuthor() {
		User author = userWithId(1L, "alice");
		Post post = new Post(author, "body");
		ReflectionTestUtils.setField(post, "id", 100L);
		when(postRepository.findById(100L)).thenReturn(Optional.of(post));

		postService.deletePost(100L, author);

		verify(postRepository).delete(post);
	}

	@Test
	void deletePostThrowsForbiddenWhenCurrentUserIsNotAuthor() {
		User author = userWithId(1L, "alice");
		User otherUser = userWithId(2L, "bob");
		Post post = new Post(author, "body");
		ReflectionTestUtils.setField(post, "id", 100L);
		when(postRepository.findById(100L)).thenReturn(Optional.of(post));

		assertThatThrownBy(() -> postService.deletePost(100L, otherUser))
				.isInstanceOf(ForbiddenPostAccessException.class);

		verify(postRepository, never()).delete(any());
	}

	@Test
	void deletePostThrowsNotFoundForUnknownPost() {
		User author = userWithId(1L, "alice");
		when(postRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> postService.deletePost(999L, author))
				.isInstanceOf(PostNotFoundException.class);

		verify(postRepository, never()).delete(any());
	}
}
