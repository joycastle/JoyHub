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
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "deployable_application")
public class DeployableApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "catalog_resource_id", nullable = false, unique = true)
    private Long catalogResourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "deployment_mode", nullable = false, length = 32)
    private DeploymentMode deploymentMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeployableApplicationStatus status = DeployableApplicationStatus.ACTIVE;

    @Column(name = "stable_url", nullable = false, length = 1024)
    private String stableUrl;

    @Column(name = "current_release_id")
    private Long currentReleaseId;

    @Version
    @Column(nullable = false)
    private long revision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeployableApplication() {
    }

    public DeployableApplication(Long catalogResourceId, DeploymentMode deploymentMode, String stableUrl) {
        this.catalogResourceId = catalogResourceId;
        this.deploymentMode = deploymentMode;
        this.stableUrl = stableUrl;
    }

    public void activate(Long releaseId) {
        this.currentReleaseId = releaseId;
        this.status = DeployableApplicationStatus.ACTIVE;
    }

    public void takeOffline() {
        this.status = DeployableApplicationStatus.OFFLINE;
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
    public Long getCatalogResourceId() { return catalogResourceId; }
    public DeploymentMode getDeploymentMode() { return deploymentMode; }
    public DeployableApplicationStatus getStatus() { return status; }
    public String getStableUrl() { return stableUrl; }
    public Long getCurrentReleaseId() { return currentReleaseId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
