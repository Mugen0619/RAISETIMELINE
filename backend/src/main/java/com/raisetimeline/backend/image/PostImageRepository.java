package com.raisetimeline.backend.image;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

	List<PostImage> findByPostIdOrderBySortOrderAsc(Long postId);

	List<PostImage> findByPostIdInOrderByPostIdAscSortOrderAsc(Collection<Long> postIds);

	void deleteByPostId(Long postId);
}
