package com.joycastle.joyhub.runner.api;

public record RunnerSwitchRequest(
        Long jobId,
        Long applicationId,
        Long releaseId,
        String slug,
        String stableUrl
) {
}
