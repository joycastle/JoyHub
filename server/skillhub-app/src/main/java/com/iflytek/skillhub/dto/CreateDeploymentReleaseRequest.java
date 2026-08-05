package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDeploymentReleaseRequest(@NotBlank @Size(max = 64) String version) {
}
