package com.raisetimeline.backend.like;

import com.raisetimeline.backend.post.PostCountProjection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LikeRepository extends JpaRepository<Like, Long> {

	Optional<Like> findByPostIdAndUserId(Long postId, Long userId);

	boolean existsByPostIdAndUserId(Long postId, Long userId);

	long countByPostId(Long postId);

	void deleteByPostId(Long postId);

	@Query("""
			SELECT l.post.id AS postId, COUNT(l) AS count
			FROM Like l
			WHERE l.post.id IN :postIds
			GROUP BY l.post.id
			""")
	List<PostCountProjection> countGroupedByPostIds(@Param("postIds") Collection<Long> postIds);

	@Query("""
			SELECT l.post.id
			FROM Like l
			WHERE l.user.id = :userId AND l.post.id IN :postIds
			""")
	List<Long> findLikedPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);
}
