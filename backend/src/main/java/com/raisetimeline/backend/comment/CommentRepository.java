package com.raisetimeline.backend.comment;

import com.raisetimeline.backend.post.PostCountProjection;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	@EntityGraph(attributePaths = "user")
	Page<Comment> findByPostId(Long postId, Pageable pageable);

	long countByPostId(Long postId);

	void deleteByPostId(Long postId);

	@Query("""
			SELECT c.post.id AS postId, COUNT(c) AS count
			FROM Comment c
			WHERE c.post.id IN :postIds
			GROUP BY c.post.id
			""")
	List<PostCountProjection> countGroupedByPostIds(@Param("postIds") Collection<Long> postIds);
}
