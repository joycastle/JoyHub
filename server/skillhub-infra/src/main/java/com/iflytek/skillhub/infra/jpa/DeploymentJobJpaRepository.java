package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.catalog.deployment.DeploymentJob;
import com.iflytek.skillhub.catalog.deployment.DeploymentJobRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeploymentJobJpaRepository
        extends JpaRepository<DeploymentJob, Long>, DeploymentJobRepository {
    List<DeploymentJob> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    @Override
    default List<DeploymentJob> findByApplicationId(Long applicationId) {
        return findByApplicationIdOrderByCreatedAtDesc(applicationId);
    }
}
