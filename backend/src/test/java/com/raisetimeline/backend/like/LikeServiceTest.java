package com.raisetimeline.backend.like;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raisetimeline.backend.post.Post;
import com.raisetimeline.backend.post.PostNotFoundException;
import com.raisetimeline.backend.post.PostRepository;
import com.raisetimeline.backend.user.User;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

	@Mock
	private LikeRepository likeRepository;

	@Mock
	private PostRepository postRepository;

	private LikeService likeService;

	@BeforeEach
	void setUp() {
		likeService = new LikeService(likeRepository, postRepository);
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
	void toggleLikeCreatesLikeWhenNotAlreadyLiked() {
		User author = userWithId(1L, "alice");
		Post post = postWithId(100L, author);
		User liker = userWithId(2L, "bob");
		when(postRepository.findById(100L)).thenReturn(Optional.of(post));
		when(likeRepository.findByPostIdAndUserId(100L, 2L)).thenReturn(Optional.empty());
		when(likeRepository.countByPostId(100L)).thenReturn(1L);

		LikeResponse response = likeService.toggleLike(100L, liker);

		ArgumentCaptor<Like> captor = ArgumentCaptor.forClass(Like.class);
		verify(likeRepository).saveAndFlush(captor.capture());
		assertThat(captor.getValue().getPost()).isSameAs(post);
		assertThat(captor.getValue().getUser()).isSameAs(liker);
		assertThat(response.liked()).isTrue();
		assertThat(response.likeCount()).isEqualTo(1L);
		verify(likeRepository, never()).delete(any());
	}

	@Test
	void toggleLikeRemovesLikeWhenAlreadyLiked() {
		User author = userWithId(1L, "alice");
		Post post = postWithId(100L, author);
		User liker = userWithId(2L, "bob");
		Like existing = new Like(post, liker);
		when(postRepository.findById(100L)).thenReturn(Optional.of(post));
		when(likeRepository.findByPostIdAndUserId(100L, 2L)).thenReturn(Optional.of(existing));
		when(likeRepository.countByPostId(100L)).thenReturn(0L);

		LikeResponse response = likeService.toggleLike(100L, liker);

		verify(likeRepository).delete(existing);
		verify(likeRepository, never()).saveAndFlush(any());
		assertThat(response.liked()).isFalse();
		assertThat(response.likeCount()).isEqualTo(0L);
	}

	@Test
	void toggleLikeAllowsLikingOwnPost() {
		User author = userWithId(1L, "alice");
		Post post = postWithId(100L, author);
		when(postRepository.findById(100L)).thenReturn(Optional.of(post));
		when(likeRepository.findByPostIdAndUserId(100L, 1L)).thenReturn(Optional.empty());
		when(likeRepository.countByPostId(100L)).thenReturn(1L);

		LikeResponse response = likeService.toggleLike(100L, author);

		assertThat(response.liked()).isTrue();
	}

	@Test
	void toggleLikeThrowsNotFoundForUnknownPost() {
		User liker = userWithId(2L, "bob");
		when(postRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> likeService.toggleLike(999L, liker)).isInstanceOf(PostNotFoundException.class);

		verify(likeRepository, never()).saveAndFlush(any());
		verify(likeRepository, never()).delete(any());
	}

	@Test
	void toggleLikeTreatsConcurrentDuplicateInsertAsIdempotentSuccess() {
		User author = userWithId(1L, "alice");
		Post post = postWithId(100L, author);
		User liker = userWithId(2L, "bob");
		when(postRepository.findById(100L)).thenReturn(Optional.of(post));
		when(likeRepository.findByPostIdAndUserId(100L, 2L)).thenReturn(Optional.empty());
		when(likeRepository.saveAndFlush(any(Like.class)))
				.thenThrow(new DataIntegrityViolationException("uk_likes_post_user violated"));
		when(likeRepository.countByPostId(100L)).thenReturn(1L);

		LikeResponse response = likeService.toggleLike(100L, liker);

		assertThat(response.liked()).isTrue();
		assertThat(response.likeCount()).isEqualTo(1L);
	}
}
