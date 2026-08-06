package com.joycastle.joyhub.runner.api;

import com.joycastle.joyhub.runner.exception.RunnerException;

public record RunnerDeploymentResult(
        boolean success,
        String errorCode,
        String summary,
        String currentReleaseId,
        String localUrl
) {
    public static RunnerDeploymentResult success(String summary, String releaseId, String localUrl) {
        return new RunnerDeploymentResult(true, null, summary, releaseId, localUrl);
    }

    public static RunnerDeploymentResult failed(RunnerException exception) {
        return new RunnerDeploymentResult(false, exception.code(), exception.getMessage(), null, null);
    }
}
