package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.infra.jpa.ResourceCategoryCode;
import com.iflytek.skillhub.infra.jpa.ResourceCategorySource;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentJpaRepository;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResourceCategoryAppServiceTest {
    private final ResourceSearchDocumentJpaRepository documentRepository = mock(ResourceSearchDocumentJpaRepository.class);
    private final ResourceSearchDocumentSyncService syncService = mock(ResourceSearchDocumentSyncService.class);
    private final SkillRepository skillRepository = mock(SkillRepository.class);
    private final CatalogResourceRepository catalogRepository = mock(CatalogResourceRepository.class);
    private ResourceCategoryAppService service;

    @BeforeEach
    void setUp() {
        service = new ResourceCategoryAppService(documentRepository, syncService, skillRepository, catalogRepository);
    }

    @Test
    void authorCanSetCategoryAndReturnToAi() {
        Skill skill = skill("author");
        ResourceSearchDocumentEntity document = document();
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(documentRepository.findByResourceTypeAndResourceId("SKILL", 1L)).thenReturn(Optional.of(document));
        when(documentRepository.save(document)).thenReturn(document);
        CatalogViewer author = new CatalogViewer("author", Map.of(), Set.of());

        service.update("skill", 1L, "data_analytics", author);
        assertThat(document.getCategoryCode()).isEqualTo(ResourceCategoryCode.DATA_ANALYTICS);
        assertThat(document.getCategorySource()).isEqualTo(ResourceCategorySource.AUTHOR);

        service.update("SKILL", 1L, null, author);
        assertThat(document.getCategoryCode()).isEqualTo(ResourceCategoryCode.OTHER);
        assertThat(document.getCategorySource()).isEqualTo(ResourceCategorySource.AI);
        assertThat(document.getGenerationStatus()).isEqualTo("PENDING");
    }

    @Test
    void rejectsNonOwnerButAllowsSuperAdmin() {
        Skill skill = skill("author");
        ResourceSearchDocumentEntity document = document();
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(documentRepository.findByResourceTypeAndResourceId("SKILL", 1L)).thenReturn(Optional.of(document));
        when(documentRepository.save(document)).thenReturn(document);

        assertThatThrownBy(() -> service.update("SKILL", 1L, "OTHER",
                new CatalogViewer("stranger", Map.of(), Set.of())))
                .isInstanceOf(DomainForbiddenException.class);

        service.update("SKILL", 1L, "OTHER",
                new CatalogViewer("admin", Map.of(), Set.of("SUPER_ADMIN")));
        assertThat(document.getCategorySource()).isEqualTo(ResourceCategorySource.AUTHOR);
    }

    @Test
    void rejectsUnknownHumanCategory() {
        assertThatThrownBy(() -> service.validateRequestedCategory("not-a-category"))
                .isInstanceOf(DomainBadRequestException.class);
    }

    private Skill skill(String ownerId) {
        Skill skill = mock(Skill.class);
        when(skill.getOwnerId()).thenReturn(ownerId);
        return skill;
    }

    private ResourceSearchDocumentEntity document() {
        return ResourceSearchDocumentEntity.basic("SKILL", 1L, null, "author", "Title", "slug", "Summary",
                "[]", "INSTALL", "PUBLIC", "ACTIVE", "Documentation", "hash");
    }
}
