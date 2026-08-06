package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.catalog.deployment.DeployableApplication;
import com.iflytek.skillhub.catalog.deployment.DeployableApplicationRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeployableApplicationJpaRepository
        extends JpaRepository<DeployableApplication, Long>, DeployableApplicationRepository {
    @Override
    Optional<DeployableApplication> findByCatalogResourceId(Long catalogResourceId);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from DeployableApplication application where application.id = :id")
    Optional<DeployableApplication> findLockedById(@Param("id") Long id);
}
