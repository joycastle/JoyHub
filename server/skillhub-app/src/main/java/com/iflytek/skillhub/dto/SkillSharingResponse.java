package com.iflytek.skillhub.dto;

import java.util.List;

/** Current explicit department access grants for a skill. */
public record SkillSharingResponse(List<SharingTargetResponse> departments, boolean restricted) {
}
