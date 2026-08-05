package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.catalog.deployment.DeployableApplication;
import com.iflytek.skillhub.catalog.deployment.DeployableApplicationRepository;
import com.iflytek.skillhub.catalog.deployment.DeploymentJob;
import com.iflytek.skillhub.catalog.deployment.DeploymentJobRepository;
import com.iflytek.skillhub.catalog.deployment.DeploymentJobStatus;
import com.iflytek.skillhub.catalog.deployment.DeploymentMode;
import com.iflytek.skillhub.catalog.deployment.DeploymentOperation;
import com.iflytek.skillhub.catalog.deployment.DeploymentRelease;
import com.iflytek.skillhub.catalog.deployment.DeploymentReleaseRepository;
import com.iflytek.skillhub.catalog.deployment.DeploymentReleaseStatus;
import com.iflytek.skillhub.catalog.domain.CatalogDomainException;
import com.iflytek.skillhub.catalog.domain.CatalogMaintenanceStatus;
import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourceDraft;
import com.iflytek.skillhub.catalog.domain.CatalogResourceKind;
import com.iflytek.skillhub.catalog.domain.CatalogResourcePolicy;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.catalog.domain.CatalogResourceStatus;
import com.iflytek.skillhub.catalog.domain.CatalogVisibilityScope;
import com.iflytek.skillhub.config.DeploymentRunnerProperties;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DeploymentStateServiceTest {
    private final DeployableApplicationRepository applicationRepository = mock(DeployableApplicationRepository.class);
    private final DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
    private final DeploymentJobRepository jobRepository = mock(DeploymentJobRepository.class);
    private final CatalogResourceRepository catalogRepository = mock(CatalogResourceRepository.class);
    private final ObjectStorageService storageService = mock(ObjectStorageService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-05T10:00:00Z"), ZoneOffset.UTC);
    private DeploymentStateService service;

    @BeforeEach
    void setUp() {
        DeploymentRunnerProperties properties = new DeploymentRunnerProperties();
        properties.setPublicOrigin("http://localhost:8090");
        properties.setPathPrefix("/apps");
        service = new DeploymentStateService(
                applicationRepository,
                releaseRepository,
                jobRepository,
                catalogRepository,
                new CatalogResourcePolicy(),
                storageService,
                properties,
                auditLogService,
                clock
        );
    }

    @Test
    void nonMaintainerCannotEnableDeployment() {
        CatalogResource catalog = catalog("owner");
        when(catalogRepository.findById(1L)).thenReturn(Optional.of(catalog));

        assertThatThrownBy(() -> service.createApplication(
                1L, DeploymentMode.STATIC, new CatalogViewer("other-user", null, null)))
                .isInstanceOfSatisfying(CatalogDomainException.class,
                        exception -> assertThat(exception.status()).isEqualTo(403));
    }

    @Test
    void successfulActivationChangesCurrentReleaseAndCatalogOnlyDuringCompletion() {
        CatalogResource catalog = catalog("owner");
        catalog.attachArtifact("catalog/1/app.zip", "app.zip", "application/zip", 128);
        DeployableApplication application = new DeployableApplication(
                1L, DeploymentMode.STATIC, "http://localhost:8090/apps/demo-app/");
        ReflectionTestUtils.setField(application, "id", 2L);

        DeploymentRelease previous = release(2L, "v1", "owner");
        ReflectionTestUtils.setField(previous, "id", 10L);
        previous.activate(clock.instant().minusSeconds(60));
        ReflectionTestUtils.setField(application, "currentReleaseId", 10L);

        DeploymentRelease target = release(2L, "v2", "owner");
        ReflectionTestUtils.setField(target, "id", 11L);
        DeploymentJob job = new DeploymentJob(2L, 11L, DeploymentOperation.DEPLOY, "owner");
        ReflectionTestUtils.setField(job, "id", 21L);

        when(jobRepository.findById(21L)).thenReturn(Optional.of(job));
        when(applicationRepository.findLockedById(2L)).thenReturn(Optional.of(application));
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(previous));
        when(releaseRepository.findById(11L)).thenReturn(Optional.of(target));
        when(catalogRepository.findById(1L)).thenReturn(Optional.of(catalog));

        service.completeActivation(21L, "deployed", new AuditRequestContext("127.0.0.1", "test"));

        assertThat(application.getCurrentReleaseId()).isEqualTo(11L);
        assertThat(target.getStatus()).isEqualTo(DeploymentReleaseStatus.ACTIVE);
        assertThat(previous.getStatus()).isEqualTo(DeploymentReleaseStatus.INACTIVE);
        assertThat(job.getStatus()).isEqualTo(DeploymentJobStatus.SUCCEEDED);
        assertThat(catalog.getStatus()).isEqualTo(CatalogResourceStatus.PUBLISHED);
        assertThat(catalog.getAccessUrl()).isEqualTo("http://localhost:8090/apps/demo-app/");
        assertThat(catalog.getVersion()).isEqualTo("v2");
        verify(auditLogService).record(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    private CatalogResource catalog(String ownerId) {
        CatalogResource resource = new CatalogResource(new CatalogResourceDraft(
                "demo-app",
                "Demo app",
                "A deployable app",
                CatalogResourceKind.ONLINE_TOOL,
                null,
                null,
                "Deployment documentation",
                null,
                null,
                null,
                null,
                null,
                Set.of(),
                null,
                CatalogMaintenanceStatus.ACTIVE,
                CatalogVisibilityScope.COMPANY,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of()
        ), ownerId);
        ReflectionTestUtils.setField(resource, "id", 1L);
        return resource;
    }

    private DeploymentRelease release(Long applicationId, String version, String ownerId) {
        return new DeploymentRelease(applicationId, version, "artifact.zip", "a".repeat(64), ownerId);
    }
}
