package com.raisetimeline.backend.image;

import com.raisetimeline.backend.post.Post;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "post_images")
public class PostImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "post_id", nullable = false)
	private Post post;

	@Column(name = "image_url", nullable = false)
	private String imageUrl;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	protected PostImage() {
	}

	public PostImage(Post post, String imageUrl, int sortOrder) {
		this.post = post;
		this.imageUrl = imageUrl;
		this.sortOrder = sortOrder;
	}

	public Long getId() {
		return id;
	}

	public Post getPost() {
		return post;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public int getSortOrder() {
		return sortOrder;
	}
}
