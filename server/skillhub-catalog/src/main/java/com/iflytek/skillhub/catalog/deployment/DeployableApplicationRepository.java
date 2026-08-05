package com.iflytek.skillhub.catalog.deployment;

import java.util.Optional;

public interface DeployableApplicationRepository {
    Optional<DeployableApplication> findById(Long id);
    Optional<DeployableApplication> findLockedById(Long id);
    Optional<DeployableApplication> findByCatalogResourceId(Long catalogResourceId);
    DeployableApplication save(DeployableApplication application);
}
