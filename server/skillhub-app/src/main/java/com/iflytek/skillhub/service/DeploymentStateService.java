package com.iflytek.skillhub.service;

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
import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourceKind;
import com.iflytek.skillhub.catalog.domain.CatalogResourcePolicy;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.config.DeploymentRunnerProperties;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeploymentStateService {
    private static final long MAX_STATIC_ZIP_SIZE = 50L * 1024L * 1024L;
    private static final Pattern VERSION_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");

    private final DeployableApplicationRepository applicationRepository;
    private final DeploymentReleaseRepository releaseRepository;
    private final DeploymentJobRepository jobRepository;
    private final CatalogResourceRepository catalogRepository;
    private final CatalogResourcePolicy catalogPolicy;
    private final ObjectStorageService storageService;
    private final DeploymentRunnerProperties properties;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public DeploymentStateService(DeployableApplicationRepository applicationRepository,
                                  DeploymentReleaseRepository releaseRepository,
                                  DeploymentJobRepository jobRepository,
                                  CatalogResourceRepository catalogRepository,
                                  CatalogResourcePolicy catalogPolicy,
                                  ObjectStorageService storageService,
                                  DeploymentRunnerProperties properties,
                                  AuditLogService auditLogService,
                                  Clock clock) {
        this.applicationRepository = applicationRepository;
        this.releaseRepository = releaseRepository;
        this.jobRepository = jobRepository;
        this.catalogRepository = catalogRepository;
        this.catalogPolicy = catalogPolicy;
        this.storageService = storageService;
        this.properties = properties;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    public DeployableApplication createApplication(Long catalogResourceId,
                                                   DeploymentMode mode,
                                                   CatalogViewer viewer) {
        CatalogResource catalog = requireDeployableCatalog(catalogResourceId, mode, viewer);
        applicationRepository.findByCatalogResourceId(catalogResourceId).ifPresent(existing -> {
            throw CatalogDomainException.conflict("error.deployment.application.exists");
        });
        return applicationRepository.save(new DeployableApplication(
                catalogResourceId,
                mode,
                properties.stableUrl(catalog.getSlug())
        ));
    }

    @Transactional
    public DeployableApplication ensureApplication(Long catalogResourceId,
                                                   DeploymentMode mode,
                                                   CatalogViewer viewer) {
        CatalogResource catalog = requireDeployableCatalog(catalogResourceId, mode, viewer);
        return applicationRepository.findByCatalogResourceId(catalogResourceId)
                .orElseGet(() -> applicationRepository.save(new DeployableApplication(
                        catalogResourceId,
                        mode,
                        properties.stableUrl(catalog.getSlug())
                )));
    }

    @Transactional
    public PendingDeployment beginDeploy(Long applicationId,
                                         String requestedVersion,
                                         CatalogViewer viewer) {
        DeployableApplication application = requireLockedApplication(applicationId);
        CatalogResource catalog = requireManagedCatalog(application, viewer);
        catalog.requireDeploymentPublishable();
        requireNoRunningJob(applicationId);

        String version = normalizeVersion(requestedVersion);
        if (releaseRepository.existsByApplicationIdAndVersion(applicationId, version)) {
            throw CatalogDomainException.conflict("error.deployment.release.version.exists", version);
        }

        byte[] artifact = readArtifact(catalog);
        String sha256 = sha256(artifact);
        String snapshotKey = "deployments/" + applicationId + "/" + UUID.randomUUID() + ".zip";
        storageService.putObject(snapshotKey, new java.io.ByteArrayInputStream(artifact), artifact.length, "application/zip");

        DeploymentRelease release = releaseRepository.save(new DeploymentRelease(
                applicationId,
                version,
                snapshotKey,
                sha256,
                viewer.userId()
        ));
        DeploymentJob job = jobRepository.save(new DeploymentJob(
                applicationId,
                release.getId(),
                DeploymentOperation.DEPLOY,
                viewer.userId()
        ));
        return new PendingDeployment(
                application.getId(),
                release.getId(),
                job.getId(),
                catalog.getSlug(),
                version,
                sha256,
                application.getStableUrl(),
                artifact,
                catalog.getArtifactFilename()
        );
    }

    @Transactional
    public PendingSwitch beginSwitch(Long applicationId,
                                     Long targetReleaseId,
                                     DeploymentOperation operation,
                                     CatalogViewer viewer) {
        DeployableApplication application = requireLockedApplication(applicationId);
        CatalogResource catalog = requireManagedCatalog(application, viewer);
        requireNoRunningJob(applicationId);
        DeploymentRelease target = requireRelease(targetReleaseId);
        if (!target.getApplicationId().equals(applicationId)
                || target.getStatus() == DeploymentReleaseStatus.FAILED
                || target.getStatus() == DeploymentReleaseStatus.DEPLOYING) {
            throw CatalogDomainException.badRequest("error.deployment.release.target.invalid");
        }
        if (operation == DeploymentOperation.ROLLBACK
                && targetReleaseId.equals(application.getCurrentReleaseId())) {
            throw CatalogDomainException.badRequest("error.deployment.release.target.current");
        }
        DeploymentJob job = jobRepository.save(new DeploymentJob(
                applicationId,
                targetReleaseId,
                operation,
                viewer.userId()
        ));
        return new PendingSwitch(
                applicationId,
                targetReleaseId,
                job.getId(),
                catalog.getSlug(),
                application.getStableUrl()
        );
    }

    @Transactional
    public PendingOffline beginOffline(Long applicationId, CatalogViewer viewer) {
        DeployableApplication application = requireLockedApplication(applicationId);
        CatalogResource catalog = requireManagedCatalog(application, viewer);
        requireNoRunningJob(applicationId);
        if (application.getCurrentReleaseId() == null) {
            throw CatalogDomainException.badRequest("error.deployment.application.neverDeployed");
        }
        DeploymentJob job = jobRepository.save(new DeploymentJob(
                applicationId,
                application.getCurrentReleaseId(),
                DeploymentOperation.OFFLINE,
                viewer.userId()
        ));
        return new PendingOffline(applicationId, job.getId(), catalog.getSlug());
    }

    @Transactional
    public void completeActivation(Long jobId,
                                   String summary,
                                   AuditRequestContext auditContext) {
        DeploymentJob job = requireRunningJob(jobId);
        DeployableApplication application = requireLockedApplication(job.getApplicationId());
        DeploymentRelease target = requireRelease(job.getReleaseId());
        Long currentReleaseId = application.getCurrentReleaseId();
        if (currentReleaseId != null && !currentReleaseId.equals(target.getId())) {
            releaseRepository.findById(currentReleaseId).ifPresent(current -> {
                current.deactivate();
                releaseRepository.save(current);
            });
        }
        Instant now = clock.instant();
        target.activate(now);
        releaseRepository.save(target);
        application.activate(target.getId());
        applicationRepository.save(application);
        CatalogResource catalog = requireCatalog(application.getCatalogResourceId());
        catalog.activateDeployment(application.getStableUrl(), target.getVersion(), now);
        catalogRepository.save(catalog);
        job.succeed(summary, now);
        jobRepository.save(job);
        recordAudit(job, auditContext);
    }

    @Transactional
    public void completeOffline(Long jobId,
                                String summary,
                                AuditRequestContext auditContext) {
        DeploymentJob job = requireRunningJob(jobId);
        DeployableApplication application = requireLockedApplication(job.getApplicationId());
        application.takeOffline();
        applicationRepository.save(application);
        CatalogResource catalog = requireCatalog(application.getCatalogResourceId());
        catalog.takeOffline();
        catalogRepository.save(catalog);
        job.succeed(summary, clock.instant());
        jobRepository.save(job);
        recordAudit(job, auditContext);
    }

    @Transactional
    public void failOperation(Long jobId,
                              String code,
                              String summary,
                              AuditRequestContext auditContext) {
        DeploymentJob job = requireRunningJob(jobId);
        if (job.getOperation() == DeploymentOperation.DEPLOY && job.getReleaseId() != null) {
            DeploymentRelease release = requireRelease(job.getReleaseId());
            release.fail(code, summary);
            releaseRepository.save(release);
        }
        job.fail(code, summary, clock.instant());
        jobRepository.save(job);
        recordAudit(job, auditContext);
    }

    private CatalogResource requireManagedCatalog(DeployableApplication application, CatalogViewer viewer) {
        CatalogResource catalog = requireCatalog(application.getCatalogResourceId());
        catalogPolicy.requireManage(catalog, viewer.userId(), viewer.superAdmin());
        return catalog;
    }

    private CatalogResource requireDeployableCatalog(Long catalogResourceId,
                                                      DeploymentMode mode,
                                                      CatalogViewer viewer) {
        if (mode != DeploymentMode.STATIC) {
            throw CatalogDomainException.badRequest("error.deployment.mode.unsupported");
        }
        CatalogResource catalog = requireCatalog(catalogResourceId);
        catalogPolicy.requireManage(catalog, viewer.userId(), viewer.superAdmin());
        if (catalog.getKind() != CatalogResourceKind.ONLINE_TOOL
                && catalog.getKind() != CatalogResourceKind.AGENT) {
            throw CatalogDomainException.badRequest("error.deployment.catalog.kind.unsupported");
        }
        return catalog;
    }

    private CatalogResource requireCatalog(Long id) {
        return catalogRepository.findById(id)
                .orElseThrow(() -> CatalogDomainException.notFound("error.catalog.notFound", id));
    }

    private DeployableApplication requireLockedApplication(Long id) {
        return applicationRepository.findLockedById(id)
                .orElseThrow(() -> CatalogDomainException.notFound("error.deployment.application.notFound", id));
    }

    private DeploymentRelease requireRelease(Long id) {
        return releaseRepository.findById(id)
                .orElseThrow(() -> CatalogDomainException.notFound("error.deployment.release.notFound", id));
    }

    private DeploymentJob requireRunningJob(Long id) {
        DeploymentJob job = jobRepository.findById(id)
                .orElseThrow(() -> CatalogDomainException.notFound("error.deployment.job.notFound", id));
        if (job.getStatus() != DeploymentJobStatus.RUNNING) {
            throw CatalogDomainException.conflict("error.deployment.job.finished", id);
        }
        return job;
    }

    private void requireNoRunningJob(Long applicationId) {
        if (jobRepository.existsByApplicationIdAndStatus(applicationId, DeploymentJobStatus.RUNNING)) {
            throw CatalogDomainException.conflict("error.deployment.application.busy");
        }
    }

    private String normalizeVersion(String value) {
        String normalized = value != null ? value.trim() : "";
        if (!VERSION_PATTERN.matcher(normalized).matches()) {
            throw CatalogDomainException.badRequest("error.deployment.release.version.invalid");
        }
        return normalized;
    }

    private byte[] readArtifact(CatalogResource catalog) {
        if (catalog.getArtifactSize() != null && catalog.getArtifactSize() > MAX_STATIC_ZIP_SIZE) {
            throw CatalogDomainException.badRequest("error.deployment.artifact.tooLarge", MAX_STATIC_ZIP_SIZE);
        }
        try (InputStream input = storageService.getObject(catalog.getArtifactStorageKey())) {
            byte[] bytes = input.readNBytes((int) MAX_STATIC_ZIP_SIZE + 1);
            if (bytes.length > MAX_STATIC_ZIP_SIZE) {
                throw CatalogDomainException.badRequest("error.deployment.artifact.tooLarge", MAX_STATIC_ZIP_SIZE);
            }
            return bytes;
        } catch (IOException exception) {
            throw CatalogDomainException.badRequest("error.catalog.artifact.readFailed");
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void recordAudit(DeploymentJob job, AuditRequestContext context) {
        auditLogService.record(
                job.getCreatedBy(),
                "DEPLOYMENT_" + job.getOperation().name() + "_" + job.getStatus().name(),
                "DEPLOYABLE_APPLICATION",
                job.getApplicationId(),
                null,
                context != null ? context.clientIp() : null,
                context != null ? context.userAgent() : null,
                "{\"jobId\":" + job.getId() + ",\"releaseId\":" + job.getReleaseId() + "}"
        );
    }

    public record PendingDeployment(
            Long applicationId,
            Long releaseId,
            Long jobId,
            String slug,
            String version,
            String sha256,
            String stableUrl,
            byte[] artifact,
            String filename
    ) {
        public PendingDeployment {
            artifact = artifact.clone();
        }

        @Override
        public byte[] artifact() {
            return artifact.clone();
        }
    }

    public record PendingSwitch(
            Long applicationId,
            Long releaseId,
            Long jobId,
            String slug,
            String stableUrl
    ) {
    }

    public record PendingOffline(Long applicationId, Long jobId, String slug) {
    }
}
