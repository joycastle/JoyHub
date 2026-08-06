package com.joycastle.joyhub.runner.api;

public record RunnerStateResponse(String slug, boolean online, String currentReleaseId) {
}
