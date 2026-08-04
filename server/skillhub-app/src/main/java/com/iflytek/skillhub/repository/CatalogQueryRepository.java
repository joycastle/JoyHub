package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourceKind;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Viewer-aware Catalog read-model query boundary. */
public interface CatalogQueryRepository {
    Page<CatalogResource> searchPublished(
            String query,
            String center,
            CatalogResourceKind kind,
            String scenario,
            Long departmentId,
            Set<Long> viewerNamespaceIds,
            boolean superAdmin,
            Pageable pageable
    );
}
