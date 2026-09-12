package com.raisetimeline.backend.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@ExtendWith(MockitoExtension.class)
class ImagePresignServiceTest {

	private static final String PUBLIC_BASE_URL = "https://test-bucket.s3.ap-northeast-1.amazonaws.com";

	@Mock
	private S3Presigner s3Presigner;

	@Mock
	private PresignedPutObjectRequest presignedPutObjectRequest;

	private ImagePresignService imagePresignService;

	@BeforeEach
	void setUp() {
		imagePresignService = new ImagePresignService(s3Presigner, "test-bucket", PUBLIC_BASE_URL, 300);
	}

	@SuppressWarnings("unchecked")
	private void stubPresignerToReturnUrl(String url) throws Exception {
		when(s3Presigner.presignPutObject(any(Consumer.class))).thenReturn(presignedPutObjectRequest);
		when(presignedPutObjectRequest.url()).thenReturn(new URI(url).toURL());
	}

	@Test
	void presignReturnsUploadUrlAndPublicImageUrlForValidRequest() throws Exception {
		stubPresignerToReturnUrl(PUBLIC_BASE_URL + "/posts/x.jpg?X-Amz-Signature=abc");

		PresignResponse response = imagePresignService.presign(new PresignRequest("image/jpeg", 1024));

		assertThat(response.uploadUrl()).contains("X-Amz-Signature");
		assertThat(response.imageUrl()).startsWith(PUBLIC_BASE_URL + "/posts/");
		assertThat(response.imageUrl()).endsWith(".jpg");
	}

	@Test
	void presignGeneratesDifferentObjectKeysForEachCall() throws Exception {
		stubPresignerToReturnUrl(PUBLIC_BASE_URL + "/posts/x.png");

		PresignResponse first = imagePresignService.presign(new PresignRequest("image/png", 1024));
		PresignResponse second = imagePresignService.presign(new PresignRequest("image/png", 1024));

		assertThat(first.imageUrl()).isNotEqualTo(second.imageUrl());
	}

	@Test
	void presignRejectsUnsupportedContentType() {
		assertThatThrownBy(() -> imagePresignService.presign(new PresignRequest("image/gif", 1024)))
				.isInstanceOf(InvalidImageException.class);
	}

	@Test
	void presignRejectsZeroOrNegativeFileSize() {
		assertThatThrownBy(() -> imagePresignService.presign(new PresignRequest("image/png", 0)))
				.isInstanceOf(InvalidImageException.class);
	}

	@Test
	void presignRejectsFileSizeOverFiveMegabytes() {
		long overLimit = 5L * 1024 * 1024 + 1;

		assertThatThrownBy(() -> imagePresignService.presign(new PresignRequest("image/png", overLimit)))
				.isInstanceOf(InvalidImageException.class);
	}

	@Test
	void presignAcceptsFileSizeExactlyAtFiveMegabyteLimit() throws Exception {
		stubPresignerToReturnUrl(PUBLIC_BASE_URL + "/posts/x.png");

		assertThatCode(() -> imagePresignService.presign(new PresignRequest("image/png", 5L * 1024 * 1024)))
				.doesNotThrowAnyException();
	}

	@Test
	void isTrustedImageUrlAcceptsUrlsUnderThePublicBaseUrl() {
		assertThat(imagePresignService.isTrustedImageUrl(PUBLIC_BASE_URL + "/posts/a.png")).isTrue();
	}

	@Test
	void isTrustedImageUrlRejectsUrlsFromOtherHosts() {
		assertThat(imagePresignService.isTrustedImageUrl("https://evil.example.com/posts/a.png")).isFalse();
	}

	@Test
	void isTrustedImageUrlRejectsNull() {
		assertThat(imagePresignService.isTrustedImageUrl(null)).isFalse();
	}

	@Test
	void maxImagesPerPostIsFour() {
		assertThat(ImagePresignService.MAX_IMAGES_PER_POST).isEqualTo(4);
	}
}
