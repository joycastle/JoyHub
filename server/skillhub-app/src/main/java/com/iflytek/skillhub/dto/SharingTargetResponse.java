package com.iflytek.skillhub.dto;

/** Active non-global namespace that can be selected as a sharing target. */
public record SharingTargetResponse(Long id, String slug, String displayName) {
}
