package com.iflytek.skillhub.infra.jpa;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Access to the shared search projection; aggregate authorization remains in application services. */
@Repository
public interface ResourceSearchDocumentJpaRepository extends JpaRepository<ResourceSearchDocumentEntity, Long> {
    @Query("""
            select d.id from ResourceSearchDocumentEntity d
            where d.searchEnabled = true
              and (d.generationStatus = 'PENDING' or
                   (d.generationStatus = 'FAILED' and d.generationNextRetryAt <= :now))
              and (d.generationLeaseUntil is null or d.generationLeaseUntil <= :now)
            order by d.generationNextRetryAt asc nulls first, d.id asc
            """)
    List<Long> findGenerationCandidates(@Param("now") Instant now,
                                         Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ResourceSearchDocumentEntity d where d.id = :id")
    Optional<ResourceSearchDocumentEntity> findForGeneration(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d from ResourceSearchDocumentEntity d
            where d.resourceType = :type and d.resourceId = :resourceId
            """)
    Optional<ResourceSearchDocumentEntity> findForRegeneration(
            @Param("type") String type,
            @Param("resourceId") Long resourceId);

    List<ResourceSearchDocumentEntity> findBySearchEnabledTrue();
    Optional<ResourceSearchDocumentEntity> findByResourceTypeAndResourceId(String resourceType, Long resourceId);
    List<ResourceSearchDocumentEntity> findTop20ByGenerationStatusAndSearchEnabledTrueOrderByUpdatedAtAsc(
            String generationStatus);
    Page<ResourceSearchDocumentEntity> findByResourceType(String resourceType, Pageable pageable);
    Page<ResourceSearchDocumentEntity> findByGenerationStatus(String generationStatus, Pageable pageable);
    Page<ResourceSearchDocumentEntity> findByResourceTypeAndGenerationStatus(String resourceType, String generationStatus,
                                                                              Pageable pageable);
}
