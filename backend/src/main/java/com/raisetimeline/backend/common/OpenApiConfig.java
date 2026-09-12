package com.raisetimeline.backend.common;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
		info = @Info(
				title = "RAISETIMELINE API",
				version = "v1",
				description = "RaiseTech中級編課題のSNSアプリ「RAISETIMELINE」のバックエンドAPI仕様書。"
						+ "認証(JWT)が必要なエンドポイントは、右上の「Authorize」からアクセストークンを入力するとSwagger UI上で実行できる。"),
		security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
		name = "bearerAuth",
		type = SecuritySchemeType.HTTP,
		scheme = "bearer",
		bearerFormat = "JWT",
		in = SecuritySchemeIn.HEADER)
public class OpenApiConfig {
}
