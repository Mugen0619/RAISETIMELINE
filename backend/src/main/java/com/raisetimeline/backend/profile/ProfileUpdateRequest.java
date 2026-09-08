package com.raisetimeline.backend.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
		@NotBlank @Size(max = 64) String displayName,
		@Size(max = 160) String bio) {
}
