package com.raisetimeline.backend.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.ModelAndView;

@ExtendWith(MockitoExtension.class)
class UnhandledExceptionLoggingResolverTest {

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpServletResponse response;

	private final UnhandledExceptionLoggingResolver resolver = new UnhandledExceptionLoggingResolver();

	private Logger logger;
	private ListAppender<ILoggingEvent> listAppender;

	@BeforeEach
	void attachListAppender() {
		logger = (Logger) LoggerFactory.getLogger(UnhandledExceptionLoggingResolver.class);
		listAppender = new ListAppender<>();
		listAppender.start();
		logger.addAppender(listAppender);
	}

	@AfterEach
	void detachListAppender() {
		logger.detachAppender(listAppender);
	}

	@Test
	void logsExceptionTypeAndMessageAndReturnsNullToDeferToOtherResolvers() {
		RuntimeException ex = new IllegalStateException("something went wrong");

		ModelAndView result = resolver.resolveException(request, response, new Object(), ex);

		assertThat(result).isNull();
		assertThat(listAppender.list).hasSize(1);
		ILoggingEvent event = listAppender.list.get(0);
		assertThat(event.getLevel().toString()).isEqualTo("ERROR");
	}
}
