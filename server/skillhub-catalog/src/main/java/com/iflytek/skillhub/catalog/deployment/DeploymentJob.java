package com.iflytek.skillhub.catalog.deployment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "deployment_job")
public class DeploymentJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "release_id")
    private Long releaseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeploymentOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeploymentJobStatus status = DeploymentJobStatus.RUNNING;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "result_code", length = 96)
    private String resultCode;

    @Column(name = "result_summary", length = 1200)
    private String resultSummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected DeploymentJob() {
    }

    public DeploymentJob(Long applicationId,
                         Long releaseId,
                         DeploymentOperation operation,
                         String createdBy) {
        this.applicationId = applicationId;
        this.releaseId = releaseId;
        this.operation = operation;
        this.createdBy = createdBy;
    }

    public void succeed(String summary, Instant now) {
        status = DeploymentJobStatus.SUCCEEDED;
        resultSummary = summary;
        finishedAt = now;
    }

    public void fail(String code, String summary, Instant now) {
        status = DeploymentJobStatus.FAILED;
        resultCode = code;
        resultSummary = summary;
        finishedAt = now;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getApplicationId() { return applicationId; }
    public Long getReleaseId() { return releaseId; }
    public DeploymentOperation getOperation() { return operation; }
    public DeploymentJobStatus getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public String getResultCode() { return resultCode; }
    public String getResultSummary() { return resultSummary; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
