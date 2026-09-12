package com.raisetimeline.backend.image;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * study-user(CLI/Terraform用の広い権限を持つIAMユーザー)とは別の、
 * S3へのPutObjectのみを許可されたアプリ専用IAMユーザーの認証情報で
 * S3Presignerを構成する。
 */
@Configuration
public class S3Config {

	@Bean
	public S3Presigner s3Presigner(
			@Value("${aws.region}") String region,
			@Value("${aws.s3.access-key-id}") String accessKeyId,
			@Value("${aws.s3.secret-access-key}") String secretAccessKey) {
		AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

		return S3Presigner.builder()
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(credentials))
				.build();
	}
}
