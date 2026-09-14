package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibleNamespace;
import com.iflytek.skillhub.domain.skill.SkillVisibleNamespaceRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillSharingServiceTest {
    private static final Long SKILL_ID = 7L;
    private static final Long PRIMARY_NAMESPACE_ID = 1L;

    private SkillRepository skillRepository;
    private NamespaceRepository namespaceRepository;
    private SkillVisibleNamespaceRepository grants;
    private SkillSharingService service;
    private Skill skill;
    private Namespace target;

    @BeforeEach
    void setUp() throws Exception {
        skillRepository = mock(SkillRepository.class);
        namespaceRepository = mock(NamespaceRepository.class);
        grants = mock(SkillVisibleNamespaceRepository.class);
        service = new SkillSharingService(skillRepository, namespaceRepository, grants);

        skill = new Skill(PRIMARY_NAMESPACE_ID, "shared-skill", "owner", SkillVisibility.PUBLIC);
        setId(skill, SKILL_ID);
        when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(skill));

        target = new Namespace("finance", "Finance", "admin");
        setId(target, 2L);
        when(namespaceRepository.findByIdIn(anyList())).thenReturn(List.of(target));
    }

    @Test
    void ownerCanReplaceWithActiveNonGlobalDepartments() {
        List<Long> ids = service.replaceDepartments(
                SKILL_ID, Set.of(2L), "owner", Map.of(), Set.of());

        assertThat(ids).containsExactly(2L);
        verify(grants).deleteBySkillId(SKILL_ID);
        verify(grants).save(any(SkillVisibleNamespace.class));
    }

    @Test
    void recipientCannotManageSharing() {
        assertThatThrownBy(() -> service.replaceDepartments(
                SKILL_ID, Set.of(2L), "recipient", Map.of(2L, NamespaceRole.MEMBER), Set.of()))
                .isInstanceOf(DomainForbiddenException.class);
    }

    @Test
    void inactiveOrGlobalDepartmentIsRejected() throws Exception {
        Namespace global = new Namespace("global", "Global", "admin");
        global.setType(com.iflytek.skillhub.domain.namespace.NamespaceType.GLOBAL);
        setId(global, 3L);
        when(namespaceRepository.findByIdIn(anyList())).thenReturn(List.of(global));

        assertThatThrownBy(() -> service.replaceDepartments(
                SKILL_ID, Set.of(3L), "owner", Map.of(), Set.of()))
                .isInstanceOf(DomainBadRequestException.class);
    }

    @Test
    void disablingRestrictionClearsGrantsWithoutWideningAccidentalState() {
        List<Long> ids = service.replaceDepartments(
                SKILL_ID, Set.of(), false, "owner", Map.of(), Set.of());

        assertThat(ids).isEmpty();
        assertThat(skill.isSharingRestricted()).isFalse();
        verify(grants).deleteBySkillId(SKILL_ID);
    }

    @Test
    void emptyRestrictedSelectionStaysRestricted() {
        List<Long> ids = service.replaceDepartments(
                SKILL_ID, Set.of(), true, "owner", Map.of(), Set.of());

        assertThat(ids).isEmpty();
        assertThat(skill.isSharingRestricted()).isTrue();
        verify(grants).deleteBySkillId(SKILL_ID);
    }

    private static void setId(Object entity, Long id) throws Exception {
        Field field = entity instanceof Skill ? Skill.class.getDeclaredField("id") : Namespace.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
