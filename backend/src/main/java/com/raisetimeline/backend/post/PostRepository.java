package com.raisetimeline.backend.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

	@EntityGraph(attributePaths = "user")
	@Override
	Page<Post> findAll(Pageable pageable);
}
