package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.catalog.deployment.DeploymentRelease;
import com.iflytek.skillhub.catalog.deployment.DeploymentReleaseRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeploymentReleaseJpaRepository
        extends JpaRepository<DeploymentRelease, Long>, DeploymentReleaseRepository {
    List<DeploymentRelease> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    @Override
    default List<DeploymentRelease> findByApplicationId(Long applicationId) {
        return findByApplicationIdOrderByCreatedAtDesc(applicationId);
    }
}
