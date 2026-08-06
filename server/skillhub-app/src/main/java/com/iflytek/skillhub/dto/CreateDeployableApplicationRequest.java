package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.catalog.deployment.DeploymentMode;
import jakarta.validation.constraints.NotNull;

public record CreateDeployableApplicationRequest(
        @NotNull Long catalogResourceId,
        @NotNull DeploymentMode deploymentMode
) {
}
