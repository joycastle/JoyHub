package com.iflytek.skillhub.dto;

/** Common acknowledgement returned by resource lifecycle and social actions. */
public record ResourceActionResponse(
        String resourceId,
        String action,
        String status
) {
}
