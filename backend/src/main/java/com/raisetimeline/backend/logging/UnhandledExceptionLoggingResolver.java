package com.raisetimeline.backend.logging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * GlobalExceptionHandlerの{@code @ExceptionHandler}群やSpring MVC標準の例外解決
 * (HttpMessageNotReadableException等)のいずれでも処理されなかった、真に予期しない例外だけを
 * ログに残す。常に{@code null}を返して他のHandlerExceptionResolverに処理を委譲するため、
 * レスポンスのステータスコード決定には一切関与しない(既存の挙動を変えない)。
 */
@Component
public class UnhandledExceptionLoggingResolver implements HandlerExceptionResolver, Ordered {

	private static final Logger log = LoggerFactory.getLogger(UnhandledExceptionLoggingResolver.class);

	@Override
	public int getOrder() {
		// 他の全てのHandlerExceptionResolverが処理できなかった例外だけを最後に受け取る
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public ModelAndView resolveException(
			HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
		log.error("unhandled exception occurred",
				kv("exceptionType", ex.getClass().getSimpleName()),
				kv("exceptionMessage", ex.getMessage()), ex);
		return null;
	}
}
