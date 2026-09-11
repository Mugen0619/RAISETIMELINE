package com.raisetimeline.backend.image;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@Service
public class ImagePresignService {

	public static final int MAX_IMAGES_PER_POST = 4;

	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
	private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

	private final S3Presigner s3Presigner;
	private final String bucketName;
	private final String publicBaseUrl;
	private final Duration presignExpiration;

	public ImagePresignService(
			S3Presigner s3Presigner,
			@Value("${aws.s3.bucket-name}") String bucketName,
			@Value("${aws.s3.public-base-url}") String publicBaseUrl,
			@Value("${aws.s3.presign-expiration-seconds}") long presignExpirationSeconds) {
		this.s3Presigner = s3Presigner;
		this.bucketName = bucketName;
		this.publicBaseUrl = publicBaseUrl;
		this.presignExpiration = Duration.ofSeconds(presignExpirationSeconds);
	}

	public PresignResponse presign(PresignRequest request) {
		String contentType = validateContentType(request.contentType());
		validateFileSize(request.fileSizeBytes());

		String objectKey = "posts/" + UUID.randomUUID() + extensionFor(contentType);

		PutObjectRequest putObjectRequest = PutObjectRequest.builder()
				.bucket(bucketName)
				.key(objectKey)
				.contentType(contentType)
				.build();

		PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(builder -> builder
				.signatureDuration(presignExpiration)
				.putObjectRequest(putObjectRequest));

		String imageUrl = publicBaseUrl + "/" + objectKey;
		return new PresignResponse(presigned.url().toString(), imageUrl);
	}

	public boolean isTrustedImageUrl(String imageUrl) {
		return imageUrl != null && imageUrl.startsWith(publicBaseUrl + "/");
	}

	private String validateContentType(String contentType) {
		String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
		if (!ALLOWED_CONTENT_TYPES.contains(normalized)) {
			throw new InvalidImageException("unsupported content type: " + contentType);
		}
		return normalized;
	}

	private void validateFileSize(long fileSizeBytes) {
		if (fileSizeBytes <= 0 || fileSizeBytes > MAX_FILE_SIZE_BYTES) {
			throw new InvalidImageException(
					"file size must be between 1 byte and " + MAX_FILE_SIZE_BYTES + " bytes");
		}
	}

	private String extensionFor(String contentType) {
		return switch (contentType) {
			case "image/jpeg" -> ".jpg";
			case "image/png" -> ".png";
			case "image/webp" -> ".webp";
			default -> throw new InvalidImageException("unsupported content type: " + contentType);
		};
	}
}
