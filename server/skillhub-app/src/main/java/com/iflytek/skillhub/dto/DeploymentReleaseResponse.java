package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.catalog.deployment.DeploymentReleaseStatus;
import java.time.Instant;

public record DeploymentReleaseResponse(
        Long id,
        String version,
        DeploymentReleaseStatus status,
        String artifactSha256,
        String failureCode,
        String failureSummary,
        String createdBy,
        Instant deployedAt,
        Instant createdAt
) {
}
