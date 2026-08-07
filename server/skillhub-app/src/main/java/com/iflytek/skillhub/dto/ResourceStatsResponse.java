package com.iflytek.skillhub.dto;

/** Shared usage counters for every resource source. */
public record ResourceStatsResponse(
        String resourceId,
        long viewCount,
        long useCount,
        long downloadCount,
        int favoriteCount,
        boolean favorited
) {
}
