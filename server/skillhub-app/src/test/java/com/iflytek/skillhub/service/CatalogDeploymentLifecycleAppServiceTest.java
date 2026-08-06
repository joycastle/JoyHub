package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.catalog.deployment.DeployableApplication;
import com.iflytek.skillhub.catalog.deployment.DeployableApplicationRepository;
import com.iflytek.skillhub.catalog.deployment.DeployableApplicationStatus;
import com.iflytek.skillhub.catalog.deployment.DeploymentJobStatus;
import com.iflytek.skillhub.catalog.deployment.DeploymentMode;
import com.iflytek.skillhub.catalog.deployment.DeploymentOperation;
import com.iflytek.skillhub.catalog.domain.CatalogDomainException;
import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourceKind;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.dto.CatalogResourceDetailResponse;
import com.iflytek.skillhub.dto.DeployableApplicationResponse;
import com.iflytek.skillhub.dto.DeploymentJobResponse;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CatalogDeploymentLifecycleAppServiceTest {
    private final CatalogResourceRepository catalogRepository = mock(CatalogResourceRepository.class);
    private final DeployableApplicationRepository applicationRepository = mock(DeployableApplicationRepository.class);
    private final CatalogResourceCommandAppService catalogCommandAppService = mock(CatalogResourceCommandAppService.class);
    private final CatalogResourceQueryAppService catalogQueryAppService = mock(CatalogResourceQueryAppService.class);
    private final DeploymentCommandAppService deploymentCommandAppService = mock(DeploymentCommandAppService.class);
    private final CatalogDeploymentLifecycleAppService service = new CatalogDeploymentLifecycleAppService(
            catalogRepository,
            applicationRepository,
            catalogCommandAppService,
            catalogQueryAppService,
            deploymentCommandAppService
    );
    private final CatalogViewer viewer = new CatalogViewer("owner", null, null);
    private final AuditRequestContext audit = new AuditRequestContext("127.0.0.1", "test");

    @Test
    void managedStaticPublishCreatesReleaseThroughDeploymentControlPlane() {
        CatalogResource catalog = catalog(true, null);
        CatalogResourceDetailResponse detail = detail();
        when(catalogRepository.findBySlug("demo-tool")).thenReturn(Optional.of(catalog));
        when(applicationRepository.findByCatalogResourceId(1L)).thenReturn(Optional.empty());
        when(deploymentCommandAppService.deployCatalogResource(1L, "2.0.0", viewer, audit))
                .thenReturn(deploymentResponse(DeploymentOperation.DEPLOY, DeploymentJobStatus.SUCCEEDED));
        when(catalogQueryAppService.detail("demo-tool", viewer)).thenReturn(detail);

        assertThat(service.publish("demo-tool", "2.0.0", viewer, audit)).isSameAs(detail);

        verify(deploymentCommandAppService).deployCatalogResource(1L, "2.0.0", viewer, audit);
        verify(catalogCommandAppService, never()).publish(any(), any());
    }

    @Test
    void externalToolPublishKeepsCatalogOnlyLifecycle() {
        CatalogResource catalog = catalog(false, "https://tools.example.com/demo");
        CatalogResourceDetailResponse detail = detail();
        when(catalogRepository.findBySlug("demo-tool")).thenReturn(Optional.of(catalog));
        when(applicationRepository.findByCatalogResourceId(1L)).thenReturn(Optional.empty());
        when(catalogCommandAppService.publish("demo-tool", viewer)).thenReturn(detail);

        assertThat(service.publish("demo-tool", null, viewer, audit)).isSameAs(detail);

        verify(deploymentCommandAppService, never()).deployCatalogResource(any(), any(), any(), any());
    }

    @Test
    void failedManagedPublishReturnsAnActionableCatalogError() {
        CatalogResource catalog = catalog(true, null);
        when(catalogRepository.findBySlug("demo-tool")).thenReturn(Optional.of(catalog));
        when(applicationRepository.findByCatalogResourceId(1L)).thenReturn(Optional.empty());
        when(deploymentCommandAppService.deployCatalogResource(1L, "2.0.0", viewer, audit))
                .thenReturn(deploymentResponse(DeploymentOperation.DEPLOY, DeploymentJobStatus.FAILED));

        assertThatThrownBy(() -> service.publish("demo-tool", "2.0.0", viewer, audit))
                .isInstanceOfSatisfying(CatalogDomainException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("error.deployment.operation.failed");
                    assertThat(exception.status()).isEqualTo(400);
                });

        verify(catalogQueryAppService, never()).detail(any(), any());
    }

    @Test
    void managedToolOfflineCallsRunnerBeforeReturningCatalogState() {
        CatalogResource catalog = catalog(true, "http://localhost:8090/apps/demo-tool/");
        DeployableApplication application = new DeployableApplication(
                1L, DeploymentMode.STATIC, "http://localhost:8090/apps/demo-tool/");
        ReflectionTestUtils.setField(application, "id", 3L);
        ReflectionTestUtils.setField(application, "currentReleaseId", 11L);
        CatalogResourceDetailResponse detail = detail();
        when(catalogRepository.findBySlug("demo-tool")).thenReturn(Optional.of(catalog));
        when(applicationRepository.findByCatalogResourceId(1L)).thenReturn(Optional.of(application));
        when(deploymentCommandAppService.offline(3L, viewer, audit))
                .thenReturn(deploymentResponse(DeploymentOperation.OFFLINE, DeploymentJobStatus.SUCCEEDED));
        when(catalogQueryAppService.detail("demo-tool", viewer)).thenReturn(detail);

        assertThat(service.takeOffline("demo-tool", viewer, audit)).isSameAs(detail);

        verify(deploymentCommandAppService).offline(3L, viewer, audit);
        verify(catalogCommandAppService, never()).takeOffline(any(), any());
    }

    private CatalogResource catalog(boolean artifactAvailable, String accessUrl) {
        CatalogResource catalog = mock(CatalogResource.class);
        when(catalog.getId()).thenReturn(1L);
        when(catalog.getKind()).thenReturn(CatalogResourceKind.ONLINE_TOOL);
        when(catalog.getArtifactStorageKey()).thenReturn(artifactAvailable ? "catalog/1/demo.zip" : null);
        when(catalog.getAccessUrl()).thenReturn(accessUrl);
        when(catalog.getVersion()).thenReturn("1.0.0");
        return catalog;
    }

    private CatalogResourceDetailResponse detail() {
        return new CatalogResourceDetailResponse(
                1L,
                "demo-tool",
                "Demo tool",
                "summary",
                CatalogResourceKind.ONLINE_TOOL.name(),
                null,
                "http://localhost:8090/apps/demo-tool/",
                "documentation",
                "2.0.0",
                null,
                null,
                null,
                null,
                Collections.emptySet(),
                null,
                null,
                "PUBLISHED",
                "ACTIVE",
                "COMPANY",
                Collections.emptyList(),
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptyList(),
                Collections.emptyList(),
                true,
                "demo.zip",
                128L,
                true,
                Instant.EPOCH,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private DeployableApplicationResponse deploymentResponse(DeploymentOperation operation,
                                                             DeploymentJobStatus status) {
        DeploymentJobResponse job = new DeploymentJobResponse(
                21L,
                3L,
                11L,
                operation,
                status,
                status == DeploymentJobStatus.FAILED ? "RUNNER_REJECTED" : null,
                status == DeploymentJobStatus.FAILED ? "index.html is missing" : "completed",
                "owner",
                null,
                null
        );
        return new DeployableApplicationResponse(
                3L,
                1L,
                "demo-tool",
                DeploymentMode.STATIC,
                DeployableApplicationStatus.ACTIVE,
                "http://localhost:8090/apps/demo-tool/",
                status == DeploymentJobStatus.SUCCEEDED ? 11L : null,
                List.of(),
                List.of(job),
                null,
                null
        );
    }
}
