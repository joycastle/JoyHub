package com.joycastle.joyhub.runner.api;

public record RunnerOfflineRequest(Long jobId, Long applicationId, String slug) {
}
