package com.iflytek.skillhub.infra.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Access to the shared search projection; aggregate authorization remains in application services. */
@Repository
public interface ResourceSearchDocumentJpaRepository extends JpaRepository<ResourceSearchDocumentEntity, Long> {
    List<ResourceSearchDocumentEntity> findBySearchEnabledTrue();
    Optional<ResourceSearchDocumentEntity> findByResourceTypeAndResourceId(String resourceType, Long resourceId);
    List<ResourceSearchDocumentEntity> findTop20ByGenerationStatusAndSearchEnabledTrueOrderByUpdatedAtAsc(
            String generationStatus);
    Page<ResourceSearchDocumentEntity> findByResourceType(String resourceType, Pageable pageable);
    Page<ResourceSearchDocumentEntity> findByGenerationStatus(String generationStatus, Pageable pageable);
    Page<ResourceSearchDocumentEntity> findByResourceTypeAndGenerationStatus(String resourceType, String generationStatus,
                                                                              Pageable pageable);
}
