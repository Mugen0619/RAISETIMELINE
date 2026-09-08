package com.raisetimeline.backend.common;

import com.raisetimeline.backend.auth.DuplicateUserException;
import com.raisetimeline.backend.auth.InvalidCredentialsException;
import com.raisetimeline.backend.auth.InvalidRefreshTokenException;
import com.raisetimeline.backend.comment.CommentNotFoundException;
import com.raisetimeline.backend.comment.ForbiddenCommentAccessException;
import com.raisetimeline.backend.follow.SelfFollowException;
import com.raisetimeline.backend.post.ForbiddenPostAccessException;
import com.raisetimeline.backend.post.PostNotFoundException;
import com.raisetimeline.backend.profile.ForbiddenProfileAccessException;
import com.raisetimeline.backend.user.UserNotFoundException;
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

	@ExceptionHandler(InvalidRefreshTokenException.class)
	public ResponseEntity<ApiError> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
		ApiError body = new ApiError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", ex.getMessage());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
	}

	@ExceptionHandler(PostNotFoundException.class)
	public ResponseEntity<ApiError> handlePostNotFound(PostNotFoundException ex) {
		ApiError body = new ApiError(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
	}

	@ExceptionHandler(ForbiddenPostAccessException.class)
	public ResponseEntity<ApiError> handleForbiddenPostAccess(ForbiddenPostAccessException ex) {
		ApiError body = new ApiError(HttpStatus.FORBIDDEN.value(), "Forbidden", ex.getMessage());
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
	}

	@ExceptionHandler(CommentNotFoundException.class)
	public ResponseEntity<ApiError> handleCommentNotFound(CommentNotFoundException ex) {
		ApiError body = new ApiError(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
	}

	@ExceptionHandler(ForbiddenCommentAccessException.class)
	public ResponseEntity<ApiError> handleForbiddenCommentAccess(ForbiddenCommentAccessException ex) {
		ApiError body = new ApiError(HttpStatus.FORBIDDEN.value(), "Forbidden", ex.getMessage());
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
	}

	@ExceptionHandler(UserNotFoundException.class)
	public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex) {
		ApiError body = new ApiError(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
	}

	@ExceptionHandler(SelfFollowException.class)
	public ResponseEntity<ApiError> handleSelfFollow(SelfFollowException ex) {
		ApiError body = new ApiError(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage());
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(ForbiddenProfileAccessException.class)
	public ResponseEntity<ApiError> handleForbiddenProfileAccess(ForbiddenProfileAccessException ex) {
		ApiError body = new ApiError(HttpStatus.FORBIDDEN.value(), "Forbidden", ex.getMessage());
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
	}
}
