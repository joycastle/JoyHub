package com.iflytek.skillhub.service.deployment;

public interface DeploymentRunnerClient {
    RunnerDeploymentResult deploy(RunnerDeploymentRequest request, byte[] artifact, String filename);
    RunnerDeploymentResult rollback(RunnerSwitchRequest request);
    RunnerDeploymentResult offline(RunnerOfflineRequest request);
    RunnerDeploymentResult restore(RunnerSwitchRequest request);
}
