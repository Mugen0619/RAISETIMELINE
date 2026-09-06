package com.raisetimeline.backend.post;

public class PostNotFoundException extends RuntimeException {

	public PostNotFoundException(String message) {
		super(message);
	}
}
