package com.iflytek.skillhub.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.catalog.domain.CatalogVisibilityScope;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

@ExtendWith(MockitoExtension.class)
class BingoFrenzyCatalogInitializerTest {

    @Mock
    private CatalogResourceRepository catalogResourceRepository;

    @Mock
    private NamespaceRepository namespaceRepository;

    @Test
    void run_assignsToolsToGestaltAndLimitsVisibilityToItsFeishuDepartment() {
        Namespace gestalt = mock(Namespace.class);
        Namespace feishuDepartment = mock(Namespace.class);
        when(gestalt.getId()).thenReturn(3L);
        when(feishuDepartment.getId()).thenReturn(19L);
        when(namespaceRepository.findBySlug("gestalt")).thenReturn(Optional.of(gestalt));
        when(namespaceRepository.findByExternalProviderAndExternalId("feishu", "od-gestalt"))
                .thenReturn(Optional.of(feishuDepartment));
        when(catalogResourceRepository.findBySourceKey(any())).thenReturn(Optional.empty());
        when(catalogResourceRepository.findBySlug(any())).thenReturn(Optional.empty());
        when(catalogResourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BingoFrenzyCatalogInitializer initializer = new BingoFrenzyCatalogInitializer(
                catalogResourceRepository,
                namespaceRepository,
                Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC),
                "feishu:owner",
                "gestalt",
                "od-gestalt"
        );

        initializer.run(mock(ApplicationArguments.class));

        ArgumentCaptor<CatalogResource> resources = ArgumentCaptor.forClass(CatalogResource.class);
        verify(catalogResourceRepository, org.mockito.Mockito.times(8)).save(resources.capture());
        List<CatalogResource> saved = resources.getAllValues();
        assertThat(saved).allSatisfy(resource -> {
            assertThat(resource.getPrimaryNamespaceId()).isEqualTo(3L);
            assertThat(resource.getVisibilityScope()).isEqualTo(CatalogVisibilityScope.DEPARTMENTS);
            assertThat(resource.getVisibleNamespaceIds()).containsExactly(19L);
            assertThat(resource.getOwnerId()).isEqualTo("feishu:owner");
        });
    }
}
