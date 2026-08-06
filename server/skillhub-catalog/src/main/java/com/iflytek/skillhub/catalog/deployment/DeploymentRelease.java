package com.iflytek.skillhub.catalog.deployment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "deployment_release")
public class DeploymentRelease {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(nullable = false, length = 64)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeploymentReleaseStatus status = DeploymentReleaseStatus.DEPLOYING;

    @Column(name = "artifact_reference", nullable = false, length = 512)
    private String artifactReference;

    @Column(name = "artifact_sha256", nullable = false, length = 64)
    private String artifactSha256;

    @Column(name = "failure_code", length = 96)
    private String failureCode;

    @Column(name = "failure_summary", length = 1200)
    private String failureSummary;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "deployed_at")
    private Instant deployedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeploymentRelease() {
    }

    public DeploymentRelease(Long applicationId,
                             String version,
                             String artifactReference,
                             String artifactSha256,
                             String createdBy) {
        this.applicationId = applicationId;
        this.version = version;
        this.artifactReference = artifactReference;
        this.artifactSha256 = artifactSha256;
        this.createdBy = createdBy;
    }

    public void activate(Instant now) {
        status = DeploymentReleaseStatus.ACTIVE;
        failureCode = null;
        failureSummary = null;
        deployedAt = now;
    }

    public void deactivate() {
        if (status == DeploymentReleaseStatus.ACTIVE) {
            status = DeploymentReleaseStatus.INACTIVE;
        }
    }

    public void fail(String code, String summary) {
        status = DeploymentReleaseStatus.FAILED;
        failureCode = code;
        failureSummary = summary;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getApplicationId() { return applicationId; }
    public String getVersion() { return version; }
    public DeploymentReleaseStatus getStatus() { return status; }
    public String getArtifactReference() { return artifactReference; }
    public String getArtifactSha256() { return artifactSha256; }
    public String getFailureCode() { return failureCode; }
    public String getFailureSummary() { return failureSummary; }
    public String getCreatedBy() { return createdBy; }
    public Instant getDeployedAt() { return deployedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
