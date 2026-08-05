package com.joycastle.joyhub.runner.api;

public record RunnerDeploymentRequest(
        Long jobId,
        Long applicationId,
        Long releaseId,
        String slug,
        String version,
        String artifactSha256,
        String stableUrl
) {
}
