package com.iflytek.skillhub.catalog.deployment;

import java.util.List;
import java.util.Optional;

public interface DeploymentJobRepository {
    Optional<DeploymentJob> findById(Long id);
    List<DeploymentJob> findByApplicationId(Long applicationId);
    boolean existsByApplicationIdAndStatus(Long applicationId, DeploymentJobStatus status);
    DeploymentJob save(DeploymentJob job);
}
