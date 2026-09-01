package com.raisetimeline.backend.common;

import com.raisetimeline.backend.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 保護対象APIの動作確認用サンプルエンドポイント。JWT認証の疎通確認に使う。
 */
@RestController
@RequestMapping("/api")
public class MeController {

	@GetMapping("/me")
	public MeResponse me(@AuthenticationPrincipal User user) {
		return new MeResponse(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName());
	}

	public record MeResponse(Long id, String username, String email, String displayName) {
	}
}
