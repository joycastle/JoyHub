package com.iflytek.skillhub.service;

import java.net.http.HttpTimeoutException;
import java.time.Duration;

/** Safe diagnostic metadata: never retains relay bodies, headers, or credentials. */
public class AiRelayException extends IllegalStateException {
    private final String code;
    private final boolean retryable;
    private final Duration retryAfter;

    public AiRelayException(String code, boolean retryable) {
        this(code, retryable, Duration.ZERO);
    }

    public AiRelayException(String code, boolean retryable, Duration retryAfter) {
        super(code);
        this.code = code;
        this.retryable = retryable;
        this.retryAfter = retryAfter;
    }

    public String code() { return code; }
    public boolean retryable() { return retryable; }
    public Duration retryAfter() { return retryAfter; }

    public static AiRelayException classify(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof AiRelayException relay) return relay;
            if (current instanceof HttpTimeoutException) return new AiRelayException("AI_TIMEOUT", true);
            if (current instanceof InterruptedException) return new AiRelayException("AI_INTERRUPTED", true);
            if (current instanceof java.io.IOException) return new AiRelayException("AI_CONNECTION_FAILED", true);
        }
        return new AiRelayException("PROFILE_GENERATION_FAILED", true);
    }
}
