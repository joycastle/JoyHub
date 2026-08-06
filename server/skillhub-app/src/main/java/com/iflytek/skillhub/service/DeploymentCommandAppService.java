package com.iflytek.skillhub.service;

import com.iflytek.skillhub.catalog.deployment.DeployableApplication;
import com.iflytek.skillhub.catalog.deployment.DeploymentMode;
import com.iflytek.skillhub.catalog.deployment.DeploymentOperation;
import com.iflytek.skillhub.dto.DeployableApplicationResponse;
import com.iflytek.skillhub.service.deployment.DeploymentRunnerClient;
import com.iflytek.skillhub.service.deployment.RunnerDeploymentRequest;
import com.iflytek.skillhub.service.deployment.RunnerDeploymentResult;
import com.iflytek.skillhub.service.deployment.RunnerOfflineRequest;
import com.iflytek.skillhub.service.deployment.RunnerSwitchRequest;
import org.springframework.stereotype.Service;

@Service
public class DeploymentCommandAppService {
    private final DeploymentStateService stateService;
    private final DeploymentQueryAppService queryAppService;
    private final DeploymentRunnerClient runnerClient;

    public DeploymentCommandAppService(DeploymentStateService stateService,
                                       DeploymentQueryAppService queryAppService,
                                       DeploymentRunnerClient runnerClient) {
        this.stateService = stateService;
        this.queryAppService = queryAppService;
        this.runnerClient = runnerClient;
    }

    public DeployableApplicationResponse create(Long catalogResourceId,
                                                DeploymentMode mode,
                                                CatalogViewer viewer) {
        DeployableApplication application = stateService.createApplication(catalogResourceId, mode, viewer);
        return queryAppService.detail(application.getId(), viewer);
    }

    public DeployableApplicationResponse deploy(Long applicationId,
                                                String version,
                                                CatalogViewer viewer,
                                                AuditRequestContext auditContext) {
        DeploymentStateService.PendingDeployment pending = stateService.beginDeploy(applicationId, version, viewer);
        RunnerDeploymentResult result = runnerClient.deploy(
                new RunnerDeploymentRequest(
                        pending.jobId(),
                        pending.applicationId(),
                        pending.releaseId(),
                        pending.slug(),
                        pending.version(),
                        pending.sha256(),
                        pending.stableUrl()
                ),
                pending.artifact(),
                pending.filename()
        );
        finishActivation(pending.jobId(), result, auditContext);
        return queryAppService.detail(applicationId, viewer);
    }

    public DeployableApplicationResponse deployCatalogResource(Long catalogResourceId,
                                                               String version,
                                                               CatalogViewer viewer,
                                                               AuditRequestContext auditContext) {
        DeployableApplication application = stateService.ensureApplication(
                catalogResourceId, DeploymentMode.STATIC, viewer);
        return deploy(application.getId(), version, viewer, auditContext);
    }

    public DeployableApplicationResponse rollback(Long applicationId,
                                                  Long releaseId,
                                                  CatalogViewer viewer,
                                                  AuditRequestContext auditContext) {
        return switchRelease(applicationId, releaseId, DeploymentOperation.ROLLBACK, viewer, auditContext);
    }

    public DeployableApplicationResponse restore(Long applicationId,
                                                 Long releaseId,
                                                 CatalogViewer viewer,
                                                 AuditRequestContext auditContext) {
        return switchRelease(applicationId, releaseId, DeploymentOperation.RESTORE, viewer, auditContext);
    }

    public DeployableApplicationResponse offline(Long applicationId,
                                                 CatalogViewer viewer,
                                                 AuditRequestContext auditContext) {
        DeploymentStateService.PendingOffline pending = stateService.beginOffline(applicationId, viewer);
        RunnerDeploymentResult result = runnerClient.offline(new RunnerOfflineRequest(
                pending.jobId(), pending.applicationId(), pending.slug()));
        if (result.success()) {
            stateService.completeOffline(pending.jobId(), result.summary(), auditContext);
        } else {
            stateService.failOperation(pending.jobId(), result.errorCode(), result.summary(), auditContext);
        }
        return queryAppService.detail(applicationId, viewer);
    }

    private DeployableApplicationResponse switchRelease(Long applicationId,
                                                        Long releaseId,
                                                        DeploymentOperation operation,
                                                        CatalogViewer viewer,
                                                        AuditRequestContext auditContext) {
        DeploymentStateService.PendingSwitch pending = stateService.beginSwitch(
                applicationId, releaseId, operation, viewer);
        RunnerSwitchRequest request = new RunnerSwitchRequest(
                pending.jobId(),
                pending.applicationId(),
                pending.releaseId(),
                pending.slug(),
                pending.stableUrl()
        );
        RunnerDeploymentResult result = operation == DeploymentOperation.ROLLBACK
                ? runnerClient.rollback(request) : runnerClient.restore(request);
        finishActivation(pending.jobId(), result, auditContext);
        return queryAppService.detail(applicationId, viewer);
    }

    private void finishActivation(Long jobId,
                                  RunnerDeploymentResult result,
                                  AuditRequestContext auditContext) {
        if (result.success()) {
            stateService.completeActivation(jobId, result.summary(), auditContext);
        } else {
            stateService.failOperation(jobId, result.errorCode(), result.summary(), auditContext);
        }
    }
}
