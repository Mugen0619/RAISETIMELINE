package com.raisetimeline.backend.image;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PresignRequest(
		@NotBlank String contentType,
		@Positive long fileSizeBytes) {
}
