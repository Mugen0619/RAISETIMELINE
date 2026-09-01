package com.raisetimeline.backend.common;

import com.raisetimeline.backend.auth.DuplicateUserException;
import com.raisetimeline.backend.auth.InvalidCredentialsException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(fe -> fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
		ApiError body = new ApiError(HttpStatus.BAD_REQUEST.value(), "Bad Request", "validation failed", fieldErrors);
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(DuplicateUserException.class)
	public ResponseEntity<ApiError> handleDuplicateUser(DuplicateUserException ex) {
		ApiError body = new ApiError(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
		ApiError body = new ApiError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", ex.getMessage());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
	}
}
