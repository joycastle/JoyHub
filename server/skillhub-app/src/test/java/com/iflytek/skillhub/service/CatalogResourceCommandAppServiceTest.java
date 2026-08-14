package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.iflytek.skillhub.catalog.domain.CatalogDomainException;
import com.iflytek.skillhub.catalog.domain.CatalogMaintenanceStatus;
import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourceDraft;
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
import org.mockito.ArgumentCaptor;
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
                mock(CatalogResourceProjectionAssembler.class),
                mock(ResourceSearchDocumentSyncService.class),
                mock(ResourceCategoryAppService.class));
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
        given(namespaceRepository.findById(42L)).willReturn(java.util.Optional.of(department));

        assertThatThrownBy(() -> service.create(request(42L), viewer(Set.of(7L))))
                .isInstanceOfSatisfying(CatalogDomainException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.code())
                                .isEqualTo("error.catalog.publishTarget.membershipRequired"));
    }

    @Test
    void createDerivesDepartmentVisibilityFromTheSelectedPublishTarget() {
        CatalogResourceService resourceService = mock(CatalogResourceService.class);
        CatalogResourceRepository resourceRepository = mock(CatalogResourceRepository.class);
        CatalogResource created = mock(CatalogResource.class);
        Namespace department = mock(Namespace.class);
        given(department.getId()).willReturn(42L);
        given(department.getSlug()).willReturn("lab");
        given(department.getStatus()).willReturn(NamespaceStatus.ACTIVE);
        given(namespaceRepository.findById(42L)).willReturn(java.util.Optional.of(department));
        given(resourceRepository.findBySlug("report-tool")).willReturn(java.util.Optional.empty());
        given(created.getKind()).willReturn(CatalogResourceKind.ONLINE_TOOL);
        given(created.getId()).willReturn(8L);
        given(resourceService.create(org.mockito.ArgumentMatchers.any(CatalogResourceDraft.class),
                org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.eq(false)))
                .willReturn(created);
        service = new CatalogResourceCommandAppService(
                resourceService, resourceRepository, namespaceRepository, mock(SkillRepository.class),
                mock(UserAccountRepository.class), mock(CatalogResourceProjectionAssembler.class),
                mock(ResourceSearchDocumentSyncService.class), mock(ResourceCategoryAppService.class));

        service.create(request(42L), viewer(Set.of(42L)));

        ArgumentCaptor<CatalogResourceDraft> draft = ArgumentCaptor.forClass(CatalogResourceDraft.class);
        verify(resourceService).create(draft.capture(), org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.eq(false));
        org.assertj.core.api.Assertions.assertThat(draft.getValue().visibilityScope())
                .isEqualTo(CatalogVisibilityScope.DEPARTMENTS);
        org.assertj.core.api.Assertions.assertThat(draft.getValue().visibleNamespaceIds()).containsExactly(42L);
    }

    @Test
    void agentCategorySuppliesLegacyRequiredScenarioWhenPublishing() {
        CatalogResourceService resourceService = mock(CatalogResourceService.class);
        CatalogResourceRepository resourceRepository = mock(CatalogResourceRepository.class);
        CatalogResource created = mock(CatalogResource.class);
        Namespace department = mock(Namespace.class);
        given(department.getId()).willReturn(42L);
        given(department.getStatus()).willReturn(NamespaceStatus.ACTIVE);
        given(namespaceRepository.findById(42L)).willReturn(java.util.Optional.of(department));
        given(resourceRepository.findBySlug("agent-test")).willReturn(java.util.Optional.empty());
        given(created.getKind()).willReturn(CatalogResourceKind.AGENT);
        given(created.getId()).willReturn(8L);
        given(resourceService.create(org.mockito.ArgumentMatchers.any(CatalogResourceDraft.class),
                org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.eq(true)))
                .willReturn(created);
        service = new CatalogResourceCommandAppService(
                resourceService, resourceRepository, namespaceRepository, mock(SkillRepository.class),
                mock(UserAccountRepository.class), mock(CatalogResourceProjectionAssembler.class),
                mock(ResourceSearchDocumentSyncService.class), mock(ResourceCategoryAppService.class));

        service.create(agentRequest("DATA_ANALYTICS"), viewer(Set.of(42L)));

        ArgumentCaptor<CatalogResourceDraft> draft = ArgumentCaptor.forClass(CatalogResourceDraft.class);
        verify(resourceService).create(draft.capture(), org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.eq(true));
        org.assertj.core.api.Assertions.assertThat(draft.getValue().scenarios()).containsExactly("DATA_ANALYTICS");
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
                CatalogMaintenanceStatus.ACTIVE, CatalogVisibilityScope.DEPARTMENTS,
                Set.of(999L), Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    private CatalogResourceRequest agentRequest(String categoryCode) {
        return new CatalogResourceRequest(
                "agent-test", "Data agent", "Analyze data", CatalogResourceKind.AGENT,
                null, "https://example.com/agent", "Usage", "1.0.0",
                null, null, null, null, Set.of(), 42L,
                CatalogMaintenanceStatus.ACTIVE, CatalogVisibilityScope.DEPARTMENTS,
                Set.of(42L), Set.of(), Set.of(), Set.of(), Set.of(), categoryCode, true);
    }
}
