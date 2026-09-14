package com.raisetimeline.backend.security;

import com.raisetimeline.backend.logging.MdcKeys;
import com.raisetimeline.backend.user.User;
import com.raisetimeline.backend.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final UserRepository userRepository;

	public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
		this.jwtService = jwtService;
		this.userRepository = userRepository;
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		String header = request.getHeader("Authorization");

		if (header != null && header.startsWith(BEARER_PREFIX)) {
			String token = header.substring(BEARER_PREFIX.length());

			if (jwtService.isValid(token)) {
				Optional<User> user = userRepository.findById(jwtService.extractUserId(token));

				if (user.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
					UsernamePasswordAuthenticationToken authentication =
							new UsernamePasswordAuthenticationToken(user.get(), null, List.of());
					authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

					SecurityContext context = SecurityContextHolder.createEmptyContext();
					context.setAuthentication(authentication);
					SecurityContextHolder.setContext(context);

					// 以降のログ(コントローラー等)にユーザーIDを自動付与する。
					// MDC自体のクリアはRequestLoggingFilterがリクエスト終了時に一括して行う。
					MDC.put(MdcKeys.USER_ID, String.valueOf(user.get().getId()));
				}
			}
		}

		filterChain.doFilter(request, response);
	}
}
