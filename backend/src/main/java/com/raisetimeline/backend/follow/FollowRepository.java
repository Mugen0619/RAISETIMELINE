package com.raisetimeline.backend.follow;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, Long> {

	Optional<Follow> findByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

	boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

	long countByFollowerId(Long followerId);

	long countByFolloweeId(Long followeeId);

	@EntityGraph(attributePaths = "followee")
	Page<Follow> findByFollowerId(Long followerId, Pageable pageable);

	@EntityGraph(attributePaths = "follower")
	Page<Follow> findByFolloweeId(Long followeeId, Pageable pageable);

	@Query("""
			SELECT f.followee.id
			FROM Follow f
			WHERE f.follower.id = :followerId AND f.followee.id IN :followeeIds
			""")
	List<Long> findFollowedUserIds(@Param("followerId") Long followerId, @Param("followeeIds") Collection<Long> followeeIds);

	@Query("SELECT f.followee.id FROM Follow f WHERE f.follower.id = :followerId")
	List<Long> findFolloweeIdsByFollowerId(@Param("followerId") Long followerId);
}
