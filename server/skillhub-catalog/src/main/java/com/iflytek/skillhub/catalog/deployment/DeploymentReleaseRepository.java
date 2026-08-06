package com.iflytek.skillhub.catalog.deployment;

import java.util.List;
import java.util.Optional;

public interface DeploymentReleaseRepository {
    Optional<DeploymentRelease> findById(Long id);
    List<DeploymentRelease> findByApplicationId(Long applicationId);
    boolean existsByApplicationIdAndVersion(Long applicationId, String version);
    boolean existsByApplicationIdAndVersionAndStatusNot(
            Long applicationId, String version, DeploymentReleaseStatus status);
    DeploymentRelease save(DeploymentRelease release);
}
