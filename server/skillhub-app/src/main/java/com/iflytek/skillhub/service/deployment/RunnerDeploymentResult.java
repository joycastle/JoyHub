package com.iflytek.skillhub.service.deployment;

public record RunnerDeploymentResult(
        boolean success,
        String errorCode,
        String summary,
        String currentReleaseId,
        String localUrl
) {
    public static RunnerDeploymentResult failed(String code, String summary) {
        return new RunnerDeploymentResult(false, code, summary, null, null);
    }
}
