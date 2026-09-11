package com.raisetimeline.backend.post;

import com.raisetimeline.backend.image.ImagePresignService;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PostRequest(
		@Size(max = 280) String body,
		@Size(max = ImagePresignService.MAX_IMAGES_PER_POST,
				message = "a post can have at most " + ImagePresignService.MAX_IMAGES_PER_POST + " images") List<String> imageUrls) {
}
