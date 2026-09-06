package com.raisetimeline.backend.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(
		@NotBlank @Size(min = 1, max = 280) String body) {
}
