package com.iflytek.skillhub.catalog.deployment;

import java.util.List;
import java.util.Optional;

public interface DeploymentReleaseRepository {
    Optional<DeploymentRelease> findById(Long id);
    List<DeploymentRelease> findByApplicationId(Long applicationId);
    boolean existsByApplicationIdAndVersion(Long applicationId, String version);
    DeploymentRelease save(DeploymentRelease release);
}
