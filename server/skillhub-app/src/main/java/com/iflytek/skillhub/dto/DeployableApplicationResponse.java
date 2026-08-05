package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.catalog.deployment.DeployableApplicationStatus;
import com.iflytek.skillhub.catalog.deployment.DeploymentMode;
import java.time.Instant;
import java.util.List;

public record DeployableApplicationResponse(
        Long id,
        Long catalogResourceId,
        String catalogSlug,
        DeploymentMode deploymentMode,
        DeployableApplicationStatus status,
        String stableUrl,
        Long currentReleaseId,
        List<DeploymentReleaseResponse> releases,
        List<DeploymentJobResponse> jobs,
        Instant createdAt,
        Instant updatedAt
) {
}
