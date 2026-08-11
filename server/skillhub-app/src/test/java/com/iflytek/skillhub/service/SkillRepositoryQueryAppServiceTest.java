package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

class SkillRepositoryQueryAppServiceTest {
    private final NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
    private final SkillRepositoryQueryAppService service = new SkillRepositoryQueryAppService(namespaceRepository);

    @Test
    void listActivePinsGlobalAsTheDefaultFirstRepository() {
        Namespace team = new Namespace("alpha-team", "Alpha team", "admin");
        Namespace global = new Namespace("global", "JoyHub公共库", "admin");
        given(namespaceRepository.findByStatus(
                org.mockito.ArgumentMatchers.eq(NamespaceStatus.ACTIVE),
                org.mockito.ArgumentMatchers.any())).willReturn(new PageImpl<>(List.of(team, global)));

        var repositories = service.listActive();

        assertThat(repositories).extracting(repository -> repository.slug())
                .containsExactly("global", "alpha-team");
        assertThat(repositories.getFirst().defaultRepository()).isTrue();
    }
}
