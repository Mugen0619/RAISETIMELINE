package com.raisetimeline.backend.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostRequest(
		@NotBlank @Size(min = 1, max = 280) String body) {
}
