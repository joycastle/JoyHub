package com.joycastle.joyhub.runner.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.joycastle.joyhub.runner.config.RunnerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TokenAuthInterceptorTest {
    private TokenAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        RunnerProperties properties = new RunnerProperties();
        properties.setToken("runner-secret");
        interceptor = new TokenAuthInterceptor(properties);
    }

    @Test
    void rejectsMissingAndIncorrectTokens() {
        MockHttpServletResponse missingResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), missingResponse, new Object())).isFalse();
        assertThat(missingResponse.getStatus()).isEqualTo(401);

        MockHttpServletRequest incorrect = new MockHttpServletRequest();
        incorrect.addHeader("Authorization", "Bearer incorrect");
        assertThat(interceptor.preHandle(incorrect, new MockHttpServletResponse(), new Object())).isFalse();
    }

    @Test
    void acceptsConfiguredBearerToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer runner-secret");
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }
}
