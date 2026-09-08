package com.raisetimeline.backend.profile;

public class ForbiddenProfileAccessException extends RuntimeException {

	public ForbiddenProfileAccessException(String message) {
		super(message);
	}
}
