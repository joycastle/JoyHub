package com.iflytek.skillhub.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CatalogResourceTest {
    private final CatalogResourcePolicy policy = new CatalogResourcePolicy();

    @Test
    void publishedDepartmentResourceIsVisibleOnlyToAllowedDepartment() {
        CatalogResource resource = new CatalogResource(draft(
                "department-agent", "# 使用说明", CatalogVisibilityScope.DEPARTMENTS, Set.of(6L)), "owner");
        resource.publish(Instant.parse("2026-08-04T00:00:00Z"));

        assertThat(policy.canView(resource, "employee", Set.of(6L), false)).isTrue();
        assertThat(policy.canView(resource, "employee", Set.of(7L), false)).isFalse();
        assertThat(policy.canView(resource, "owner", Set.of(), false)).isTrue();
        assertThat(policy.canView(resource, "admin", Set.of(), true)).isTrue();
    }

    @Test
    void publishingRequiresTheSingleCompanionDocument() {
        CatalogResource resource = new CatalogResource(draft(
                "missing-doc", null, CatalogVisibilityScope.COMPANY, Set.of()), "owner");

        assertThatThrownBy(() -> resource.publish(Instant.now()))
                .isInstanceOf(CatalogDomainException.class)
                .hasMessageContaining("error.catalog.documentation.required");
        assertThat(resource.getStatus()).isEqualTo(CatalogResourceStatus.DRAFT);
    }

    @Test
    void slugCannotChangeAfterCreation() {
        CatalogResource resource = new CatalogResource(draft(
                "stable-slug", "# 文档", CatalogVisibilityScope.COMPANY, Set.of()), "owner");

        assertThatThrownBy(() -> resource.update(draft(
                "changed-slug", "# 文档", CatalogVisibilityScope.COMPANY, Set.of())))
                .isInstanceOf(CatalogDomainException.class)
                .hasMessageContaining("error.catalog.slug.immutable");
    }

    private CatalogResourceDraft draft(String slug,
                                       String documentation,
                                       CatalogVisibilityScope visibility,
                                       Set<Long> departments) {
        return new CatalogResourceDraft(
                slug,
                "测试资源",
                "用于验证 Catalog 领域边界",
                CatalogResourceKind.AGENT,
                null,
                "https://example.test/agent",
                documentation,
                "1.0.0",
                6L,
                CatalogMaintenanceStatus.ACTIVE,
                visibility,
                departments,
                Set.of("研发提效"),
                Set.of("测试"),
                Set.of(),
                Set.of()
        );
    }
}
