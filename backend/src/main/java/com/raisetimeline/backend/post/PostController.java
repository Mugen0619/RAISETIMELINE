package com.raisetimeline.backend.post;

import com.raisetimeline.backend.user.User;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class PostController {

	private final PostService postService;

	public PostController(PostService postService) {
		this.postService = postService;
	}

	@PostMapping
	public ResponseEntity<PostResponse> createPost(
			@AuthenticationPrincipal User author,
			@Valid @RequestBody PostRequest request) {
		PostResponse response = postService.createPost(author, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping
	public PagedModel<PostResponse> getTimeline(
			@PageableDefault(size = 20, sort = { "createdAt", "id" }, direction = Sort.Direction.DESC) Pageable pageable) {
		return new PagedModel<>(postService.getTimeline(pageable));
	}

	@PutMapping("/{id}")
	public ResponseEntity<PostResponse> updatePost(
			@PathVariable Long id,
			@AuthenticationPrincipal User currentUser,
			@Valid @RequestBody PostRequest request) {
		PostResponse response = postService.updatePost(id, currentUser, request);
		return ResponseEntity.ok(response);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deletePost(
			@PathVariable Long id,
			@AuthenticationPrincipal User currentUser) {
		postService.deletePost(id, currentUser);
		return ResponseEntity.noContent().build();
	}
}
