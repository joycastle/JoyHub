package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
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
        given(namespaceRepository.findByIdIn(anyList())).willReturn(List.of(teamB, archived, teamA));

        var targets = service.list(Map.of(
                1L, NamespaceRole.MEMBER,
                2L, NamespaceRole.ADMIN,
                3L, NamespaceRole.OWNER), Set.of("USER"));

        assertThat(targets).extracting(target -> target.slug()).containsExactly("team-a", "team-b");
        assertThat(targets).allSatisfy(target -> assertThat(target.supportedResourceTypes())
                .containsExactlyInAnyOrder("SKILL", "TOOL", "AGENT"));
    }

    private Namespace namespace(Long id, String slug, NamespaceStatus status) {
        Namespace namespace = new Namespace(slug, slug, "admin");
        ReflectionTestUtils.setField(namespace, "id", id);
        namespace.setStatus(status);
        return namespace;
    }
}
