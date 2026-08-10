package com.iflytek.skillhub.dto;

/** One homepage recommendation with a transparent, user-visible reason. */
public record RecommendedResourceResponse(
        UnifiedResourceSearchItemResponse resource,
        String reason
) {
}
