package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.catalog.deployment.DeploymentJobStatus;
import com.iflytek.skillhub.catalog.deployment.DeploymentOperation;
import java.time.Instant;

public record DeploymentJobResponse(
        Long id,
        Long applicationId,
        Long releaseId,
        DeploymentOperation operation,
        DeploymentJobStatus status,
        String resultCode,
        String resultSummary,
        String createdBy,
        Instant createdAt,
        Instant finishedAt
) {
}
