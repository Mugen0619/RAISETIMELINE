package com.raisetimeline.backend.comment;

public class ForbiddenCommentAccessException extends RuntimeException {

	public ForbiddenCommentAccessException(String message) {
		super(message);
	}
}
