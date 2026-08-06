package com.iflytek.skillhub.service;

import com.iflytek.skillhub.catalog.deployment.DeployableApplication;
import com.iflytek.skillhub.catalog.deployment.DeployableApplicationRepository;
import com.iflytek.skillhub.catalog.deployment.DeploymentJobStatus;
import com.iflytek.skillhub.catalog.deployment.DeploymentOperation;
import com.iflytek.skillhub.catalog.domain.CatalogDomainException;
import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourceKind;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.dto.CatalogResourceDetailResponse;
import com.iflytek.skillhub.dto.DeployableApplicationResponse;
import com.iflytek.skillhub.dto.DeploymentJobResponse;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Routes Catalog lifecycle actions through the deployment control plane when JoyHub hosts the tool. */
@Service
public class CatalogDeploymentLifecycleAppService {
    private final CatalogResourceRepository catalogRepository;
    private final DeployableApplicationRepository applicationRepository;
    private final CatalogResourceCommandAppService catalogCommandAppService;
    private final CatalogResourceQueryAppService catalogQueryAppService;
    private final DeploymentCommandAppService deploymentCommandAppService;

    public CatalogDeploymentLifecycleAppService(CatalogResourceRepository catalogRepository,
                                                DeployableApplicationRepository applicationRepository,
                                                CatalogResourceCommandAppService catalogCommandAppService,
                                                CatalogResourceQueryAppService catalogQueryAppService,
                                                DeploymentCommandAppService deploymentCommandAppService) {
        this.catalogRepository = catalogRepository;
        this.applicationRepository = applicationRepository;
        this.catalogCommandAppService = catalogCommandAppService;
        this.catalogQueryAppService = catalogQueryAppService;
        this.deploymentCommandAppService = deploymentCommandAppService;
    }

    public CatalogResourceDetailResponse publish(String slug,
                                                 String requestedVersion,
                                                 CatalogViewer viewer,
                                                 AuditRequestContext auditContext) {
        CatalogResource catalog = requireCatalog(slug);
        Optional<DeployableApplication> application = applicationRepository.findByCatalogResourceId(catalog.getId());
        if (!isManagedStatic(catalog, application)) {
            return catalogCommandAppService.publish(slug, viewer);
        }

        DeployableApplicationResponse deployment = deploymentCommandAppService.deployCatalogResource(
                catalog.getId(), version(requestedVersion, catalog.getVersion()), viewer, auditContext);
        requireSucceeded(deployment, DeploymentOperation.DEPLOY);
        return catalogQueryAppService.detail(slug, viewer);
    }

    public CatalogResourceDetailResponse takeOffline(String slug,
                                                     CatalogViewer viewer,
                                                     AuditRequestContext auditContext) {
        CatalogResource catalog = requireCatalog(slug);
        Optional<DeployableApplication> application = applicationRepository.findByCatalogResourceId(catalog.getId());
        if (application.isEmpty() || application.get().getCurrentReleaseId() == null) {
            return catalogCommandAppService.takeOffline(slug, viewer);
        }

        DeployableApplicationResponse deployment = deploymentCommandAppService.offline(
                application.get().getId(), viewer, auditContext);
        requireSucceeded(deployment, DeploymentOperation.OFFLINE);
        return catalogQueryAppService.detail(slug, viewer);
    }

    private CatalogResource requireCatalog(String slug) {
        return catalogRepository.findBySlug(slug)
                .orElseThrow(() -> CatalogDomainException.notFound("error.catalog.notFound", slug));
    }

    private String version(String requestedVersion, String catalogVersion) {
        return requestedVersion != null && !requestedVersion.isBlank()
                ? requestedVersion.trim() : catalogVersion;
    }

    private boolean isManagedStatic(CatalogResource catalog,
                                    Optional<DeployableApplication> application) {
        if (application.isPresent()) {
            return true;
        }
        return catalog.getKind() == CatalogResourceKind.ONLINE_TOOL
                && catalog.getArtifactStorageKey() != null
                && !catalog.getArtifactStorageKey().isBlank()
                && (catalog.getAccessUrl() == null || catalog.getAccessUrl().isBlank());
    }

    private void requireSucceeded(DeployableApplicationResponse deployment,
                                  DeploymentOperation operation) {
        DeploymentJobResponse job = deployment.jobs().stream()
                .filter(candidate -> candidate.operation() == operation)
                .findFirst()
                .orElseThrow(() -> CatalogDomainException.badRequest(
                        "error.deployment.operation.resultMissing", operation));
        if (job.status() != DeploymentJobStatus.SUCCEEDED) {
            throw CatalogDomainException.badRequest(
                    "error.deployment.operation.failed",
                    job.resultCode() != null ? job.resultCode() : "UNKNOWN",
                    job.resultSummary() != null ? job.resultSummary() : "");
        }
    }
}
