package com.raisetimeline.backend.logging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * リクエストごとにtraceIdを発行してMDCに設定し、Spring Securityを含む全フィルターより先に実行する。
 * ここで設定したMDCはリクエスト処理中の全ログに自動的に含まれ、finallyで必ずクリアする
 * (スレッドプールで別リクエストに使い回された際の値の混入を防ぐため)。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		MDC.put(MdcKeys.TRACE_ID, UUID.randomUUID().toString());
		long startTime = System.currentTimeMillis();

		try {
			filterChain.doFilter(request, response);
		} catch (Exception ex) {
			// Spring MVC内(DispatcherServlet)で解決できた例外はここまで伝播せず、doFilter()は
			// 正常終了する。ここに到達するのはSecurity/認証フィルター等、MVCのディスパッチより
			// 手前で発生した例外であり、responseのステータスがまだ200のままなので明示的に補う。
			if (!response.isCommitted()) {
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			}
			throw ex;
		} finally {
			long durationMs = System.currentTimeMillis() - startTime;
			log.info("http request completed",
					kv("httpStatus", response.getStatus()),
					kv("endpoint", request.getRequestURI()),
					kv("method", request.getMethod()),
					kv("duration_ms", durationMs));
			MDC.clear();
		}
	}
}
