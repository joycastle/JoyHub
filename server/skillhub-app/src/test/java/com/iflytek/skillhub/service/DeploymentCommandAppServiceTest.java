package com.iflytek.skillhub.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.service.deployment.DeploymentRunnerClient;
import com.iflytek.skillhub.service.deployment.RunnerDeploymentResult;
import org.junit.jupiter.api.Test;

class DeploymentCommandAppServiceTest {
    private final DeploymentStateService stateService = mock(DeploymentStateService.class);
    private final DeploymentQueryAppService queryService = mock(DeploymentQueryAppService.class);
    private final DeploymentRunnerClient runnerClient = mock(DeploymentRunnerClient.class);
    private final DeploymentCommandAppService service = new DeploymentCommandAppService(
            stateService, queryService, runnerClient);

    @Test
    void failedRunnerDeploymentRecordsFailureWithoutActivatingRelease() {
        CatalogViewer viewer = new CatalogViewer("owner", null, null);
        AuditRequestContext audit = new AuditRequestContext("127.0.0.1", "test");
        DeploymentStateService.PendingDeployment pending = new DeploymentStateService.PendingDeployment(
                1L, 11L, 21L, "demo-app", "v1", "a".repeat(64),
                "http://localhost/apps/demo-app/", new byte[]{1}, "app.zip");
        when(stateService.beginDeploy(1L, "v1", viewer)).thenReturn(pending);
        when(runnerClient.deploy(org.mockito.ArgumentMatchers.any(), eq(new byte[]{1}), eq("app.zip")))
                .thenReturn(RunnerDeploymentResult.failed("INDEX_HTML_REQUIRED", "index.html is missing"));

        service.deploy(1L, "v1", viewer, audit);

        verify(stateService).failOperation(21L, "INDEX_HTML_REQUIRED", "index.html is missing", audit);
        verify(stateService, never()).completeActivation(eq(21L), org.mockito.ArgumentMatchers.any(), eq(audit));
    }

    @Test
    void successfulRunnerDeploymentActivatesRelease() {
        CatalogViewer viewer = new CatalogViewer("owner", null, null);
        AuditRequestContext audit = new AuditRequestContext("127.0.0.1", "test");
        DeploymentStateService.PendingDeployment pending = new DeploymentStateService.PendingDeployment(
                1L, 11L, 21L, "demo-app", "v1", "a".repeat(64),
                "http://localhost/apps/demo-app/", new byte[]{1}, "app.zip");
        when(stateService.beginDeploy(1L, "v1", viewer)).thenReturn(pending);
        when(runnerClient.deploy(org.mockito.ArgumentMatchers.any(), eq(new byte[]{1}), eq("app.zip")))
                .thenReturn(new RunnerDeploymentResult(true, null, "deployed", "11", "http://static/demo-app/"));

        service.deploy(1L, "v1", viewer, audit);

        verify(stateService).completeActivation(21L, "deployed", audit);
        verify(stateService, never()).failOperation(
                eq(21L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), eq(audit));
    }
}
