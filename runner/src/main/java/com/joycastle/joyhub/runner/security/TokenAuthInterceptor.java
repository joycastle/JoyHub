package com.joycastle.joyhub.runner.security;

import com.joycastle.joyhub.runner.config.RunnerProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TokenAuthInterceptor implements HandlerInterceptor {
    private final byte[] expected;

    public TokenAuthInterceptor(RunnerProperties properties) {
        String token = properties.getToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("JOYHUB_RUNNER_TOKEN is required");
        }
        expected = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        byte[] supplied = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7).getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (!MessageDigest.isEqual(expected, supplied)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        return true;
    }
}
