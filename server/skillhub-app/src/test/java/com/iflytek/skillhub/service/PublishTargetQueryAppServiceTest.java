package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PublishTargetQueryAppServiceTest {
    private final NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
    private final PublishTargetQueryAppService service = new PublishTargetQueryAppService(namespaceRepository);

    @Test
    void listUsesOnlyCurrentUsersActiveDepartmentsForEveryResourceType() {
        Namespace teamB = namespace(2L, "team-b", NamespaceStatus.ACTIVE);
        Namespace archived = namespace(3L, "archived", NamespaceStatus.ARCHIVED);
        Namespace teamA = namespace(1L, "team-a", NamespaceStatus.ACTIVE);
        Namespace global = namespace(4L, "global", NamespaceStatus.ACTIVE);
        given(namespaceRepository.findByIdIn(anyList())).willReturn(List.of(teamB, archived, teamA, global));

        var targets = service.list(Map.of(
                1L, NamespaceRole.MEMBER,
                2L, NamespaceRole.ADMIN,
                3L, NamespaceRole.OWNER,
                4L, NamespaceRole.MEMBER), Set.of("USER"));

        assertThat(targets).extracting(target -> target.slug()).containsExactly("global", "team-a", "team-b");
        assertThat(targets).allSatisfy(target -> assertThat(target.supportedResourceTypes())
                .containsExactlyInAnyOrder("SKILL", "TOOL", "AGENT"));
    }

    @Test
    void listCollapsesFeishuDuplicatesButKeepsUniqueCurrentDepartments() {
        Namespace global = namespace(1L, "global", NamespaceStatus.ACTIVE);
        global.setType(NamespaceType.GLOBAL);
        Namespace canonicalLab = namespace(8L, "lab", NamespaceStatus.ACTIVE);
        Namespace duplicateLab = namespace(18L, "feishu-dept-duplicate", NamespaceStatus.ACTIVE);
        duplicateLab.setDisplayName("部门8 Lab");
        duplicateLab.bindExternalIdentity("feishu", "od-lab");
        Namespace operations = namespace(19L, "feishu-dept-operations", NamespaceStatus.ACTIVE);
        operations.setDisplayName("运维组");
        operations.bindExternalIdentity("feishu", "od-operations");
        given(namespaceRepository.findByIdIn(anyList()))
                .willReturn(List.of(global, canonicalLab, duplicateLab, operations));

        var targets = service.list(Map.of(
                1L, NamespaceRole.MEMBER,
                8L, NamespaceRole.MEMBER,
                18L, NamespaceRole.MEMBER,
                19L, NamespaceRole.MEMBER), Set.of("USER"));

        assertThat(targets).extracting(target -> target.slug())
                .containsExactly("global", "feishu-dept-operations", "lab");
    }

    private Namespace namespace(Long id, String slug, NamespaceStatus status) {
        Namespace namespace = new Namespace(slug, slug, "admin");
        ReflectionTestUtils.setField(namespace, "id", id);
        namespace.setStatus(status);
        return namespace;
    }
}
