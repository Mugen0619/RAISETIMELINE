package com.raisetimeline.backend.image;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts/images")
public class ImagePresignController {

	private final ImagePresignService imagePresignService;

	public ImagePresignController(ImagePresignService imagePresignService) {
		this.imagePresignService = imagePresignService;
	}

	@PostMapping("/presign")
	public PresignResponse presign(@Valid @RequestBody PresignRequest request) {
		return imagePresignService.presign(request);
	}
}
