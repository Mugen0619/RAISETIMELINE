package com.raisetimeline.backend.common;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.raisetimeline.backend.auth.DuplicateUserException;
import com.raisetimeline.backend.auth.InvalidCredentialsException;
import com.raisetimeline.backend.auth.InvalidRefreshTokenException;
import com.raisetimeline.backend.comment.CommentNotFoundException;
import com.raisetimeline.backend.comment.ForbiddenCommentAccessException;
import com.raisetimeline.backend.follow.SelfFollowException;
import com.raisetimeline.backend.image.InvalidImageException;
import com.raisetimeline.backend.post.ForbiddenPostAccessException;
import com.raisetimeline.backend.post.InvalidPostContentException;
import com.raisetimeline.backend.post.PostNotFoundException;
import com.raisetimeline.backend.profile.ForbiddenProfileAccessException;
import com.raisetimeline.backend.user.UserNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(fe -> fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
		logRejection(HttpStatus.BAD_REQUEST, ex);
		ApiError body = new ApiError(HttpStatus.BAD_REQUEST.value(), "Bad Request", "validation failed", fieldErrors);
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(DuplicateUserException.class)
	public ResponseEntity<ApiError> handleDuplicateUser(DuplicateUserException ex) {
		return buildAndLog(HttpStatus.CONFLICT, "Conflict", ex);
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
		return buildAndLog(HttpStatus.UNAUTHORIZED, "Unauthorized", ex);
	}

	@ExceptionHandler(InvalidRefreshTokenException.class)
	public ResponseEntity<ApiError> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
		return buildAndLog(HttpStatus.UNAUTHORIZED, "Unauthorized", ex);
	}

	@ExceptionHandler(PostNotFoundException.class)
	public ResponseEntity<ApiError> handlePostNotFound(PostNotFoundException ex) {
		return buildAndLog(HttpStatus.NOT_FOUND, "Not Found", ex);
	}

	@ExceptionHandler(ForbiddenPostAccessException.class)
	public ResponseEntity<ApiError> handleForbiddenPostAccess(ForbiddenPostAccessException ex) {
		return buildAndLog(HttpStatus.FORBIDDEN, "Forbidden", ex);
	}

	@ExceptionHandler(CommentNotFoundException.class)
	public ResponseEntity<ApiError> handleCommentNotFound(CommentNotFoundException ex) {
		return buildAndLog(HttpStatus.NOT_FOUND, "Not Found", ex);
	}

	@ExceptionHandler(ForbiddenCommentAccessException.class)
	public ResponseEntity<ApiError> handleForbiddenCommentAccess(ForbiddenCommentAccessException ex) {
		return buildAndLog(HttpStatus.FORBIDDEN, "Forbidden", ex);
	}

	@ExceptionHandler(UserNotFoundException.class)
	public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex) {
		return buildAndLog(HttpStatus.NOT_FOUND, "Not Found", ex);
	}

	@ExceptionHandler(SelfFollowException.class)
	public ResponseEntity<ApiError> handleSelfFollow(SelfFollowException ex) {
		return buildAndLog(HttpStatus.BAD_REQUEST, "Bad Request", ex);
	}

	@ExceptionHandler(ForbiddenProfileAccessException.class)
	public ResponseEntity<ApiError> handleForbiddenProfileAccess(ForbiddenProfileAccessException ex) {
		return buildAndLog(HttpStatus.FORBIDDEN, "Forbidden", ex);
	}

	@ExceptionHandler(InvalidImageException.class)
	public ResponseEntity<ApiError> handleInvalidImage(InvalidImageException ex) {
		return buildAndLog(HttpStatus.BAD_REQUEST, "Bad Request", ex);
	}

	@ExceptionHandler(InvalidPostContentException.class)
	public ResponseEntity<ApiError> handleInvalidPostContent(InvalidPostContentException ex) {
		return buildAndLog(HttpStatus.BAD_REQUEST, "Bad Request", ex);
	}

	private ResponseEntity<ApiError> buildAndLog(HttpStatus status, String reason, RuntimeException ex) {
		logRejection(status, ex);
		ApiError body = new ApiError(status.value(), reason, ex.getMessage());
		return ResponseEntity.status(status).body(body);
	}

	private void logRejection(HttpStatus status, Exception ex) {
		log.warn("request rejected with {}",
				status.value(),
				kv("exceptionType", ex.getClass().getSimpleName()),
				kv("exceptionMessage", ex.getMessage()));
	}
}
