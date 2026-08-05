package com.iflytek.skillhub.service.deployment;

public record RunnerOfflineRequest(Long jobId, Long applicationId, String slug) {
}
