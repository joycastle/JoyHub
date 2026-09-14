package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

/** Replaces the complete explicit department access list for a skill. */
public record SkillSharingRequest(@NotNull Set<Long> namespaceIds, @NotNull Boolean restricted) {
}
