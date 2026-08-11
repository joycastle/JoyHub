package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.catalog.domain.CatalogDomainException;
import com.iflytek.skillhub.catalog.domain.CatalogMaintenanceStatus;
import com.iflytek.skillhub.catalog.domain.CatalogResourceKind;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.catalog.domain.CatalogResourceService;
import com.iflytek.skillhub.catalog.domain.CatalogVisibilityScope;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.CatalogResourceRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatalogResourceCommandAppServiceTest {
    private NamespaceRepository namespaceRepository;
    private CatalogResourceCommandAppService service;

    @BeforeEach
    void setUp() {
        namespaceRepository = mock(NamespaceRepository.class);
        service = new CatalogResourceCommandAppService(
                mock(CatalogResourceService.class),
                mock(CatalogResourceRepository.class),
                namespaceRepository,
                mock(SkillRepository.class),
                mock(UserAccountRepository.class),
                mock(CatalogResourceProjectionAssembler.class));
    }

    @Test
    void createRequiresOneSharedPublishTarget() {
        assertThatThrownBy(() -> service.create(request(null), viewer(Set.of())))
                .isInstanceOfSatisfying(CatalogDomainException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.code())
                                .isEqualTo("error.catalog.publishTarget.required"));
    }

    @Test
    void createRejectsTargetOutsideViewerDepartments() {
        Namespace department = mock(Namespace.class);
        given(department.getId()).willReturn(42L);
        given(department.getStatus()).willReturn(NamespaceStatus.ACTIVE);
        given(namespaceRepository.findByIdIn(List.of(42L))).willReturn(List.of(department));

        assertThatThrownBy(() -> service.create(request(42L), viewer(Set.of(7L))))
                .isInstanceOfSatisfying(CatalogDomainException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.code())
                                .isEqualTo("error.catalog.publishTarget.membershipRequired"));
    }

    private CatalogViewer viewer(Set<Long> namespaceIds) {
        Map<Long, com.iflytek.skillhub.domain.namespace.NamespaceRole> roles = namespaceIds.stream()
                .collect(java.util.stream.Collectors.toMap(
                        id -> id,
                        ignored -> com.iflytek.skillhub.domain.namespace.NamespaceRole.MEMBER));
        return new CatalogViewer("user-1", roles, Set.of("USER"));
    }

    private CatalogResourceRequest request(Long targetId) {
        return new CatalogResourceRequest(
                "report-tool", "Report tool", "Generate reports", CatalogResourceKind.ONLINE_TOOL,
                null, "https://example.com", "Usage", "1.0.0",
                null, null, null, null, Set.of(), targetId,
                CatalogMaintenanceStatus.ACTIVE, CatalogVisibilityScope.COMPANY,
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false);
    }
}
