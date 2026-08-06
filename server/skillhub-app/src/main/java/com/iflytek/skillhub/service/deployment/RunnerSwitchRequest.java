package com.iflytek.skillhub.service.deployment;

public record RunnerSwitchRequest(
        Long jobId,
        Long applicationId,
        Long releaseId,
        String slug,
        String stableUrl
) {
}
