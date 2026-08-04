package com.iflytek.skillhub.filter;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingResponseWrapper;

class RequestLoggingFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private ListAppender<ILoggingEvent> appender;

    @AfterEach
    void tearDown() {
        if (appender != null) {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void doFilterInternal_omitsRequestAndResponseBodies()
            throws ServletException, IOException {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        String requestBody = "{\"username\":\"alice\",\"password\":\"super-secret\"}";
        String responseBody = "x".repeat(5_000);
        attachAppender();

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContentType("application/json");
        request.setContent(requestBody.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        FilterChain filterChain = (req, res) -> {
            req.getReader().lines().count();
            res.setContentType("application/json");
            res.getWriter().write(responseBody);
        };

        filter.doFilter(request, response, filterChain);

        List<String> loggedMessages = loggedMessages();
        assertThat(loggedMessages).anyMatch(message -> message.contains("POST /api/test"));
        assertThat(loggedMessages).noneMatch(message -> message.contains("Body:"));
        assertThat(loggedMessages).noneMatch(message -> message.contains("super-secret"));
        assertThat(loggedMessages).noneMatch(message -> message.contains("Response Body:"));
        assertThat(response.getContentAsString()).isEqualTo(responseBody);
    }

    @Test
    void doFilterInternal_skipsActuatorEndpoints()
            throws ServletException, IOException {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        attachAppender();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = (req, res) -> {};

        filter.doFilter(request, response, filterChain);

        assertThat(loggedMessages()).noneMatch(message -> message.contains("/actuator/health"));
    }

    @Test
    void doFilterInternal_skipsOtherSseEndpointsWithoutWrappingResponse()
            throws ServletException, IOException {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        attachAppender();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/web/scan/sse");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = (req, res) -> {
            assertThat(res).isSameAs(response);
            res.setContentType("text/event-stream");
            res.getWriter().write("event:connected\n");
            res.getWriter().flush();
        };

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader("Content-Length")).isNull();
        assertThat(response.getHeader("X-Accel-Buffering")).isNull();
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isNull();
        assertThat(response.getContentAsString()).isEqualTo("event:connected\n");
        assertThat(loggedMessages()).noneMatch(message -> message.contains("/api/web/scan/sse"));
    }

    @Test
    void doFilterInternal_logsCoreSummaryFields()
            throws ServletException, IOException {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        attachAppender();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/skills");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = (req, res) -> {};

        filter.doFilter(request, response, filterChain);

        assertThat(loggedMessages()).anySatisfy(message -> {
            assertThat(message).contains("GET /api/v1/skills");
            assertThat(message).contains("200");
            assertThat(message).contains("127.0.0.1");
            assertThat(message).contains("ms");
        });
        assertThat(loggedMessages()).noneMatch(message -> message.contains("Headers: {"));
    }

    @Test
    void doFilterInternal_shouldBypassCachingWrapperForNotificationSse() throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/web/notifications/sse");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ServletResponse> responseSeenByChain = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) -> {
            responseSeenByChain.set(servletResponse);
            servletResponse.getWriter().write("event: connected\n");
            servletResponse.flushBuffer();
        };

        filter.doFilter(request, response, chain);

        assertThat(responseSeenByChain.get()).isSameAs(response);
        assertThat(response.getHeader("X-Accel-Buffering")).isEqualTo("no");
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-cache, no-transform");
        assertThat(response.getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(response.getContentAsString()).contains("event: connected");
    }

    @Test
    void doFilterInternal_shouldKeepCachingWrapperForRegularApiResponses() throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/web/notifications/unread-count");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ServletResponse> responseSeenByChain = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) -> {
            responseSeenByChain.set(servletResponse);
            servletResponse.getWriter().write("{\"count\":1}");
        };

        filter.doFilter(request, response, chain);

        assertThat(responseSeenByChain.get()).isInstanceOf(ContentCachingResponseWrapper.class);
        assertThat(response.getContentAsString()).isEqualTo("{\"count\":1}");
    }

    private void attachAppender() {
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    private List<String> loggedMessages() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
