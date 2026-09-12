package com.raisetimeline.backend.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raisetimeline.backend.comment.CommentRepository;
import com.raisetimeline.backend.follow.FollowRepository;
import com.raisetimeline.backend.image.ImagePresignService;
import com.raisetimeline.backend.image.PostImage;
import com.raisetimeline.backend.image.PostImageRepository;
import com.raisetimeline.backend.like.LikeRepository;
import com.raisetimeline.backend.user.User;
import com.raisetimeline.backend.user.UserNotFoundException;
import com.raisetimeline.backend.user.UserRepository;
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

	@Mock
	private CommentRepository commentRepository;

	@Mock
	private LikeRepository likeRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private FollowRepository followRepository;

	@Mock
	private PostImageRepository postImageRepository;

	@Mock
	private ImagePresignService imagePresignService;

	private PostService postService;

	@BeforeEach
	void setUp() {
		postService = new PostService(postRepository, commentRepository, likeRepository, userRepository,
				followRepository, postImageRepository, imagePresignService);
	}

	private static User userWithId(long id, String username) {
		User user = new User(username, username + "@example.com", "hashed", username);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	@Test
	void createPostSavesPostAuthoredByCurrentUserWithZeroCounts() {
		User author = userWithId(1L, "alice");
		when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

		PostResponse response = postService.createPost(author, new PostRequest("hello world", null));

		ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
		verify(postRepository).save(captor.capture());
		assertThat(captor.getValue().getUser()).isSameAs(author);
		assertThat(captor.getValue().getBody()).isEqualTo("hello world");
		assertThat(response.userId()).isEqualTo(1L);
		assertThat(response.username()).isEqualTo("alice");
		assertThat(response.body()).isEqualTo("hello world");
		assertThat(response.commentCount()).isZero();
		assertThat(response.likeCount()).isZero();
		assertThat(response.likedByMe()).isFalse();
	}

	@Test
	void createPostThrowsWhenBodyAndImagesAreBothEmpty() {
		User author = userWithId(1L, "alice");

		assertThatThrownBy(() -> postService.createPost(author, new PostRequest("", null)))
				.isInstanceOf(InvalidPostContentException.class);
		assertThatThrownBy(() -> postService.createPost(author, new PostRequest(null, List.of())))
				.isInstanceOf(InvalidPostContentException.class);

		verify(postRepository, never()).save(any());
	}

	@Test
	void createPostThrowsWhenImageUrlIsNotFromTheConfiguredBucket() {
		User author = userWithId(1L, "alice");
		String untrustedUrl = "https://evil.example.com/image.png";
		when(imagePresignService.isTrustedImageUrl(untrustedUrl)).thenReturn(false);

		assertThatThrownBy(() -> postService.createPost(author, new PostRequest("hello", List.of(untrustedUrl))))
				.isInstanceOf(InvalidPostContentException.class);

		verify(postRepository, never()).save(any());
	}

	@Test
	void createPostAllowsImageOnlyPostWithoutBody() {
		User author = userWithId(1L, "alice");
		String trustedUrl = "https://bucket.s3.amazonaws.com/posts/image.png";
		when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(imagePresignService.isTrustedImageUrl(trustedUrl)).thenReturn(true);

		PostResponse response = postService.createPost(author, new PostRequest(null, List.of(trustedUrl)));

		assertThat(response.body()).isNull();
		assertThat(response.imageUrls()).containsExactly(trustedUrl);
	}

	@Test
	@SuppressWarnings("unchecked")
	void createPostPersistsImagesInOrderWithSortOrder() {
		User author = userWithId(1L, "alice");
		List<String> imageUrls = List.of(
				"https://bucket.s3.amazonaws.com/posts/a.png",
				"https://bucket.s3.amazonaws.com/posts/b.png");
		when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(imagePresignService.isTrustedImageUrl(any())).thenReturn(true);

		PostResponse response = postService.createPost(author, new PostRequest("hello", imageUrls));

		ArgumentCaptor<List<PostImage>> captor = ArgumentCaptor.forClass(List.class);
		verify(postImageRepository).saveAll(captor.capture());
		List<PostImage> savedImages = captor.getValue();
		assertThat(savedImages).hasSize(2);
		assertThat(savedImages.get(0).getImageUrl()).isEqualTo(imageUrls.get(0));
		assertThat(savedImages.get(0).getSortOrder()).isZero();
		assertThat(savedImages.get(1).getImageUrl()).isEqualTo(imageUrls.get(1));
		assertThat(savedImages.get(1).getSortOrder()).isEqualTo(1);
		assertThat(response.imageUrls()).containsExactlyElementsOf(imageUrls);
	}

	@Test
	void getTimelineMapsPageOfPostsWithBulkCommentAndLikeCounts() {
		User author = userWithId(1L, "alice");
		Post post = new Post(author, "hello world");
		ReflectionTestUtils.setField(post, "id", 100L);
		Pageable pageable = PageRequest.of(0, 20);
		when(postRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(post), pageable, 1));
		when(commentRepository.countGroupedByPostIds(List.of(100L))).thenReturn(List.of(countOf(100L, 3)));
		when(likeRepository.countGroupedByPostIds(List.of(100L))).thenReturn(List.of(countOf(100L, 5)));
		when(likeRepository.findLikedPostIds(eq(1L), eq(List.of(100L)))).thenReturn(List.of(100L));

		var page = postService.getTimeline(pageable, 1L);

		assertThat(page.getTotalElements()).isEqualTo(1);
		PostResponse response = page.getContent().get(0);
		assertThat(response.id()).isEqualTo(100L);
		assertThat(response.username()).isEqualTo("alice");
		assertThat(response.commentCount()).isEqualTo(3);
		assertThat(response.likeCount()).isEqualTo(5);
		assertThat(response.likedByMe()).isTrue();
	}

	@Test
	void getTimelineSkipsCountQueriesWhenPageIsEmpty() {
		Pageable pageable = PageRequest.of(0, 20);
		when(postRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

		var page = postService.getTimeline(pageable, 1L);

		assertThat(page.getTotalElements()).isZero();
		verify(commentRepository, never()).countGroupedByPostIds(any());
		verify(likeRepository, never()).countGroupedByPostIds(any());
		verify(likeRepository, never()).findLikedPostIds(anyLong(), any());
	}

	@Test
	void getTimelineIncludesImageUrlsFromBatchFetchedPostImages() {
		User author = userWithId(1L, "alice");
		Post post = new Post(author, "hello world");
		ReflectionTestUtils.setField(post, "id", 100L);
		Pageable pageable = PageRequest.of(0, 20);
		when(postRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(post), pageable, 1));
		when(commentRepository.countGroupedByPostIds(List.of(100L))).thenReturn(List.of());
		when(likeRepository.countGroupedByPostIds(List.of(100L))).thenReturn(List.of());
		when(likeRepository.findLikedPostIds(eq(1L), eq(List.of(100L)))).thenReturn(List.of());
		PostImage image = new PostImage(post, "https://bucket.s3.amazonaws.com/posts/a.png", 0);
		when(postImageRepository.findByPostIdInOrderByPostIdAscSortOrderAsc(List.of(100L))).thenReturn(List.of(image));

		var page = postService.getTimeline(pageable, 1L);

		assertThat(page.getContent().get(0).imageUrls()).containsExactly("https://bucket.s3.amazonaws.com/posts/a.png");
	}

	@Test
	void getFollowingTimelineReturnsPostsFromFolloweesWithBulkCounts() {
		User followee = userWithId(2L, "bob");
		Post post = new Post(followee, "hello from bob");
		ReflectionTestUtils.setField(post, "id", 100L);
		Pageable pageable = PageRequest.of(0, 20);
		when(followRepository.findFolloweeIdsByFollowerId(1L)).thenReturn(List.of(2L, 3L));
		when(postRepository.findByUserIdIn(List.of(2L, 3L), pageable)).thenReturn(new PageImpl<>(List.of(post), pageable, 1));
		when(commentRepository.countGroupedByPostIds(List.of(100L))).thenReturn(List.of(countOf(100L, 1)));
		when(likeRepository.countGroupedByPostIds(List.of(100L))).thenReturn(List.of(countOf(100L, 4)));
		when(likeRepository.findLikedPostIds(eq(1L), eq(List.of(100L)))).thenReturn(List.of());

		var page = postService.getFollowingTimeline(1L, pageable);

		assertThat(page.getTotalElements()).isEqualTo(1);
		PostResponse response = page.getContent().get(0);
		assertThat(response.username()).isEqualTo("bob");
		assertThat(response.commentCount()).isEqualTo(1);
		assertThat(response.likeCount()).isEqualTo(4);
		assertThat(response.likedByMe()).isFalse();
	}

	@Test
	void getFollowingTimelineReturnsEmptyPageWithoutQueryingPostsWhenNotFollowingAnyone() {
		Pageable pageable = PageRequest.of(0, 20);
		when(followRepository.findFolloweeIdsByFollowerId(1L)).thenReturn(List.of());

		var page = postService.getFollowingTimeline(1L, pageable);

		assertThat(page.getTotalElements()).isZero();
		verify(postRepository, never()).findByUserIdIn(any(), any());
	}

	@Test
	void getPostsByUserReturnsPageOfPostsWithBulkCounts() {
		User author = userWithId(1L, "alice");
		Post post = new Post(author, "hello world");
		ReflectionTestUtils.setField(post, "id", 100L);
		Pageable pageable = PageRequest.of(0, 20);
		when(userRepository.existsById(1L)).thenReturn(true);
		when(postRepository.findByUserId(1L, pageable)).thenReturn(new PageImpl<>(List.of(post), pageable, 1));
		when(commentRepository.countGroupedByPostIds(List.of(100L))).thenReturn(List.of(countOf(100L, 1)));
		when(likeRepository.countGroupedByPostIds(List.of(100L))).thenReturn(List.of(countOf(100L, 2)));
		when(likeRepository.findLikedPostIds(eq(9L), eq(List.of(100L)))).thenReturn(List.of());

		var page = postService.getPostsByUser(1L, pageable, 9L);

		assertThat(page.getTotalElements()).isEqualTo(1);
		assertThat(page.getContent().get(0).commentCount()).isEqualTo(1);
		assertThat(page.getContent().get(0).likeCount()).isEqualTo(2);
		assertThat(page.getContent().get(0).likedByMe()).isFalse();
	}

	@Test
	void getPostsByUserThrowsNotFoundForUnknownUser() {
		Pageable pageable = PageRequest.of(0, 20);
		when(userRepository.existsById(999L)).thenReturn(false);

		assertThatThrownBy(() -> postService.getPostsByUser(999L, pageable, 1L))
				.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void getPostReturnsPostWithCountsAndLikedByMeFlag() {
		User author = userWithId(1L, "alice");
		Post post = new Post(author, "hello world");
		ReflectionTestUtils.setField(post, "id", 100L);
		when(postRepository.findById(100L)).thenReturn(Optional.of(post));
		when(commentRepository.countByPostId(100L)).thenReturn(2L);
		when(likeRepository.countByPostId(100L)).thenReturn(4L);
		when(likeRepository.existsByPostIdAndUserId(100L, 9L)).thenReturn(true);

		PostResponse response = postService.getPost(100L, 9L);

		assertThat(response.commentCount()).isEqualTo(2);
		assertThat(response.likeCount()).isEqualTo(4);
		assertThat(response.likedByMe()).isTrue();
	}

	@Test
	void getPostReturnsPostWithImageUrlsInSortOrder() {
		User author = userWithId(1L, "alice");
		Post post = new Post(author, "hello world");
		ReflectionTestUtils.setField(post, "id", 100L);
		when(postRepository.findById(100L)).thenReturn(Optional.of(post));
		PostImage image = new PostImage(post, "https://bucket.s3.amazonaws.com/posts/a.png", 0);
		when(postImageRepository.findByPostIdOrderBySortOrderAsc(100L)).thenReturn(List.of(image));

		PostResponse response = postService.getPost(100L, 9L);

		assertThat(response.imageUrls()).containsExactly("https://bucket.s3.amazonaws.com/posts/a.png");
	}

	@Test
	void getPostThrowsNotFoundForUnknownPost() {
		when(postRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> postService.getPost(999L, 1L)).isInstanceOf(PostNotFoundException.class);
	}

	@Test
	void updatePostChangesBodyWhenCurrentUserIsAuthor() {
		User author = userWithId(1L, "alice");
		Post post = new Post(author, "old body");
		ReflectionTestUtils.setField(post, "id", 100L);
		when(postRepository.findById(100L)).thenReturn(Optional.of(post));

		PostResponse response = postService.updatePost(100L, author, new PostRequest("new body", null));

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

		PostResponse response = postService.updatePost(100L, author, new PostRequest("new body", null));

		assertThat(response.updatedAt()).isAfter(staleUpdatedAt);
	}

	@Test
	@SuppressWarnings("unchecked")
	void updatePostReplacesExistingImagesWithNewOnes() {
		User author = userWithId(1L, "alice");
		Post post = new Post(author, "old body");
		ReflectionTestUtils.setField(post, "id", 100L);
		when(postRepository.findById(100L)).thenReturn(Optional.of(post));
		String newImageUrl = "https://bucket.s3.amazonaws.com/posts/new.png";
		when(imagePresignService.isTrustedImageUrl(newImageUrl)).thenReturn(true);

		PostResponse response = postService.updatePost(100L, author, new PostRequest("new body", List.of(newImageUrl)));

		verify(postImageRepository).deleteByPostId(100L);
		ArgumentCaptor<List<PostImage>> captor = ArgumentCaptor.forClass(List.class);
		verify(postImageRepository).saveAll(captor.capture());
		assertThat(captor.getValue().get(0).getImageUrl()).isEqualTo(newImageUrl);
		assertThat(response.imageUrls()).containsExactly(newImageUrl);
	}

	@Test
	void updatePostThrowsWhenImageUrlIsNotFromTheConfiguredBucket() {
		User author = userWithId(1L, "alice");
		Post post = new Post(author, "old body");
		ReflectionTestUtils.setField(post, "id", 100L);
		when(postRepository.findById(100L)).thenReturn(Optional.of(post));
		String untrustedUrl = "https://evil.example.com/image.png";
		when(imagePresignService.isTrustedImageUrl(untrustedUrl)).thenReturn(false);

		assertThatThrownBy(() -> postService.updatePost(100L, author, new PostRequest("new body", List.of(untrustedUrl))))
				.isInstanceOf(InvalidPostContentException.class);

		assertThat(post.getBody()).isEqualTo("old body");
		verify(postImageRepository, never()).deleteByPostId(any());
	}

	@Test
	void updatePostChecksOwnershipBeforeValidatingImageContentSo404TakesPrecedenceOverContentErrors() {
		User author = userWithId(1L, "alice");
		when(postRepository.findById(999L)).thenReturn(Optional.empty());
		String untrustedUrl = "https://evil.example.com/image.png";

		assertThatThrownBy(() -> postService.updatePost(999L, author, new PostRequest("new body", List.of(untrustedUrl))))
				.isInstanceOf(PostNotFoundException.class);

		verify(imagePresignService, never()).isTrustedImageUrl(any());
	}

	@Test
	void updatePostThrowsForbiddenWhenCurrentUserIsNotAuthor() {
		User author = userWithId(1L, "alice");
		User otherUser = userWithId(2L, "bob");
		Post post = new Post(author, "old body");
		ReflectionTestUtils.setField(post, "id", 100L);
		when(postRepository.findById(100L)).thenReturn(Optional.of(post));

		assertThatThrownBy(() -> postService.updatePost(100L, otherUser, new PostRequest("hijacked", null)))
				.isInstanceOf(ForbiddenPostAccessException.class);

		assertThat(post.getBody()).isEqualTo("old body");
	}

	@Test
	void updatePostThrowsNotFoundForUnknownPost() {
		User author = userWithId(1L, "alice");
		when(postRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> postService.updatePost(999L, author, new PostRequest("new body", null)))
				.isInstanceOf(PostNotFoundException.class);
	}

	@Test
	void deletePostRemovesPostAndItsCommentsAndLikesWhenCurrentUserIsAuthor() {
		User author = userWithId(1L, "alice");
		Post post = new Post(author, "body");
		ReflectionTestUtils.setField(post, "id", 100L);
		when(postRepository.findById(100L)).thenReturn(Optional.of(post));

		postService.deletePost(100L, author);

		verify(likeRepository).deleteByPostId(100L);
		verify(commentRepository).deleteByPostId(100L);
		verify(postImageRepository).deleteByPostId(100L);
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
		verify(likeRepository, never()).deleteByPostId(any());
		verify(commentRepository, never()).deleteByPostId(any());
		verify(postImageRepository, never()).deleteByPostId(any());
	}

	@Test
	void deletePostThrowsNotFoundForUnknownPost() {
		User author = userWithId(1L, "alice");
		when(postRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> postService.deletePost(999L, author))
				.isInstanceOf(PostNotFoundException.class);

		verify(postRepository, never()).delete(any());
	}

	private static PostCountProjection countOf(long postId, long count) {
		return new PostCountProjection() {
			@Override
			public Long getPostId() {
				return postId;
			}

			@Override
			public long getCount() {
				return count;
			}
		};
	}
}
