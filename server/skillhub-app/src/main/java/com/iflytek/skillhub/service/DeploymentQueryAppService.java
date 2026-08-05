package com.iflytek.skillhub.service;

import com.iflytek.skillhub.catalog.deployment.DeployableApplication;
import com.iflytek.skillhub.catalog.deployment.DeployableApplicationRepository;
import com.iflytek.skillhub.catalog.deployment.DeploymentJob;
import com.iflytek.skillhub.catalog.deployment.DeploymentJobRepository;
import com.iflytek.skillhub.catalog.deployment.DeploymentRelease;
import com.iflytek.skillhub.catalog.deployment.DeploymentReleaseRepository;
import com.iflytek.skillhub.catalog.domain.CatalogDomainException;
import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourcePolicy;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.dto.DeployableApplicationResponse;
import com.iflytek.skillhub.dto.DeploymentJobResponse;
import com.iflytek.skillhub.dto.DeploymentReleaseResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeploymentQueryAppService {
    private final DeployableApplicationRepository applicationRepository;
    private final DeploymentReleaseRepository releaseRepository;
    private final DeploymentJobRepository jobRepository;
    private final CatalogResourceRepository catalogRepository;
    private final CatalogResourcePolicy catalogPolicy;

    public DeploymentQueryAppService(DeployableApplicationRepository applicationRepository,
                                     DeploymentReleaseRepository releaseRepository,
                                     DeploymentJobRepository jobRepository,
                                     CatalogResourceRepository catalogRepository,
                                     CatalogResourcePolicy catalogPolicy) {
        this.applicationRepository = applicationRepository;
        this.releaseRepository = releaseRepository;
        this.jobRepository = jobRepository;
        this.catalogRepository = catalogRepository;
        this.catalogPolicy = catalogPolicy;
    }

    @Transactional(readOnly = true)
    public DeployableApplicationResponse detail(Long id, CatalogViewer viewer) {
        DeployableApplication application = requireApplication(id);
        CatalogResource catalog = requireManagedCatalog(application, viewer);
        return new DeployableApplicationResponse(
                application.getId(),
                catalog.getId(),
                catalog.getSlug(),
                application.getDeploymentMode(),
                application.getStatus(),
                application.getStableUrl(),
                application.getCurrentReleaseId(),
                releaseRepository.findByApplicationId(id).stream().map(this::releaseResponse).toList(),
                jobRepository.findByApplicationId(id).stream().map(this::jobResponse).toList(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public DeploymentJobResponse job(Long id, CatalogViewer viewer) {
        DeploymentJob job = jobRepository.findById(id)
                .orElseThrow(() -> CatalogDomainException.notFound("error.deployment.job.notFound", id));
        requireManagedCatalog(requireApplication(job.getApplicationId()), viewer);
        return jobResponse(job);
    }

    private DeployableApplication requireApplication(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> CatalogDomainException.notFound("error.deployment.application.notFound", id));
    }

    private CatalogResource requireManagedCatalog(DeployableApplication application, CatalogViewer viewer) {
        CatalogResource catalog = catalogRepository.findById(application.getCatalogResourceId())
                .orElseThrow(() -> CatalogDomainException.notFound("error.catalog.notFound", application.getCatalogResourceId()));
        catalogPolicy.requireManage(catalog, viewer.userId(), viewer.superAdmin());
        return catalog;
    }

    private DeploymentReleaseResponse releaseResponse(DeploymentRelease release) {
        return new DeploymentReleaseResponse(
                release.getId(),
                release.getVersion(),
                release.getStatus(),
                release.getArtifactSha256(),
                release.getFailureCode(),
                release.getFailureSummary(),
                release.getCreatedBy(),
                release.getDeployedAt(),
                release.getCreatedAt()
        );
    }

    private DeploymentJobResponse jobResponse(DeploymentJob job) {
        return new DeploymentJobResponse(
                job.getId(),
                job.getApplicationId(),
                job.getReleaseId(),
                job.getOperation(),
                job.getStatus(),
                job.getResultCode(),
                job.getResultSummary(),
                job.getCreatedBy(),
                job.getCreatedAt(),
                job.getFinishedAt()
        );
    }
}
