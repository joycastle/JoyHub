package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotNull;

public record DeploymentTargetRequest(@NotNull Long targetReleaseId) {
}
