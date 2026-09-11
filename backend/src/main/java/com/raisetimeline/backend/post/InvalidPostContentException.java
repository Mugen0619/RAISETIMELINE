package com.raisetimeline.backend.post;

public class InvalidPostContentException extends RuntimeException {

	public InvalidPostContentException(String message) {
		super(message);
	}
}
