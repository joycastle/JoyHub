package com.iflytek.skillhub.dto;

public record DiscoverySuggestionResponse(
        String type,
        Long id,
        String title,
        String description,
        String kind,
        String slug,
        String namespace,
        String accessUrl,
        String usage,
        String evidence,
        String source
) {
}
