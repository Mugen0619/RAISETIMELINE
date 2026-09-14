package com.raisetimeline.backend.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class RequestLoggingFilterTest {

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpServletResponse response;

	@Mock
	private FilterChain filterChain;

	private final RequestLoggingFilter filter = new RequestLoggingFilter();

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void setsTraceIdInMdcWhileFilterChainRuns() throws Exception {
		when(request.getRequestURI()).thenReturn("/api/posts");
		when(request.getMethod()).thenReturn("GET");
		when(response.getStatus()).thenReturn(200);

		String[] traceIdSeenByDownstream = new String[1];
		org.mockito.Mockito.doAnswer(invocation -> {
			traceIdSeenByDownstream[0] = MDC.get(MdcKeys.TRACE_ID);
			return null;
		}).when(filterChain).doFilter(request, response);

		filter.doFilter(request, response, filterChain);

		assertThat(traceIdSeenByDownstream[0]).isNotBlank();
		verify(filterChain).doFilter(request, response);
	}

	@Test
	void generatesDifferentTraceIdForEachRequest() throws Exception {
		when(request.getRequestURI()).thenReturn("/api/posts");
		when(request.getMethod()).thenReturn("GET");
		when(response.getStatus()).thenReturn(200);

		String[] firstTraceId = new String[1];
		String[] secondTraceId = new String[1];
		org.mockito.Mockito.doAnswer(invocation -> {
			firstTraceId[0] = MDC.get(MdcKeys.TRACE_ID);
			return null;
		}).doAnswer(invocation -> {
			secondTraceId[0] = MDC.get(MdcKeys.TRACE_ID);
			return null;
		}).when(filterChain).doFilter(request, response);

		filter.doFilter(request, response, filterChain);
		filter.doFilter(request, response, filterChain);

		assertThat(firstTraceId[0]).isNotEqualTo(secondTraceId[0]);
	}

	@Test
	void clearsMdcAfterRequestCompletes() throws Exception {
		when(request.getRequestURI()).thenReturn("/api/posts");
		when(request.getMethod()).thenReturn("GET");
		when(response.getStatus()).thenReturn(200);

		filter.doFilter(request, response, filterChain);

		assertThat(MDC.get(MdcKeys.TRACE_ID)).isNull();
	}

	@Test
	void clearsMdcEvenWhenDownstreamThrows() throws Exception {
		when(request.getRequestURI()).thenReturn("/api/posts");
		when(request.getMethod()).thenReturn("GET");
		org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(filterChain).doFilter(request, response);

		try {
			filter.doFilter(request, response, filterChain);
		} catch (RuntimeException expected) {
			// ログ出力・MDCクリアが実行されることを検証したいだけなので例外は握りつぶす
		}

		assertThat(MDC.get(MdcKeys.TRACE_ID)).isNull();
	}

	@Test
	void setsInternalServerErrorStatusWhenDownstreamThrowsBeforeResponseIsCommitted() throws Exception {
		when(request.getRequestURI()).thenReturn("/api/posts");
		when(request.getMethod()).thenReturn("GET");
		when(response.isCommitted()).thenReturn(false);
		org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(filterChain).doFilter(request, response);

		try {
			filter.doFilter(request, response, filterChain);
		} catch (RuntimeException expected) {
			// setStatus呼び出しの検証が目的なので例外は握りつぶす
		}

		verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
	}

	@Test
	void doesNotOverrideStatusWhenResponseIsAlreadyCommitted() throws Exception {
		when(request.getRequestURI()).thenReturn("/api/posts");
		when(request.getMethod()).thenReturn("GET");
		when(response.isCommitted()).thenReturn(true);
		org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(filterChain).doFilter(request, response);

		try {
			filter.doFilter(request, response, filterChain);
		} catch (RuntimeException expected) {
			// setStatusが呼ばれないことの検証が目的なので例外は握りつぶす
		}

		verify(response, org.mockito.Mockito.never()).setStatus(org.mockito.ArgumentMatchers.anyInt());
	}
}
