package com.raisetimeline.backend.post;

public class ForbiddenPostAccessException extends RuntimeException {

	public ForbiddenPostAccessException(String message) {
		super(message);
	}
}
