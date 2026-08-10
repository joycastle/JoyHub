package com.iflytek.skillhub.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.catalog.domain.CatalogResource;
import com.iflytek.skillhub.catalog.domain.CatalogResourceKind;
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
class BingoVoyageCatalogInitializerTest {

    @Mock
    private CatalogResourceRepository catalogResourceRepository;

    @Mock
    private NamespaceRepository namespaceRepository;

    @Test
    void run_importsBusinessToolsAndLimitsVisibilityToTheConfiguredDepartment() {
        Namespace gestalt = mock(Namespace.class);
        Namespace feishuDepartment = mock(Namespace.class);
        when(gestalt.getId()).thenReturn(3L);
        when(feishuDepartment.getId()).thenReturn(29L);
        when(namespaceRepository.findBySlug("gestalt")).thenReturn(Optional.of(gestalt));
        when(namespaceRepository.findByExternalProviderAndExternalId("feishu", "od-bingo-voyage"))
                .thenReturn(Optional.of(feishuDepartment));
        when(catalogResourceRepository.findBySourceKey(any())).thenReturn(Optional.empty());
        when(catalogResourceRepository.findBySlug(any())).thenReturn(Optional.empty());
        when(catalogResourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BingoVoyageCatalogInitializer initializer = new BingoVoyageCatalogInitializer(
                catalogResourceRepository,
                namespaceRepository,
                Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC),
                "feishu:bv-owner",
                "gestalt",
                "od-bingo-voyage"
        );

        initializer.run(mock(ApplicationArguments.class));

        ArgumentCaptor<CatalogResource> resources = ArgumentCaptor.forClass(CatalogResource.class);
        verify(catalogResourceRepository, times(11)).save(resources.capture());
        List<CatalogResource> saved = resources.getAllValues();
        assertThat(saved).allSatisfy(resource -> {
            assertThat(resource.getKind()).isEqualTo(CatalogResourceKind.ONLINE_TOOL);
            assertThat(resource.getPrimaryNamespaceId()).isEqualTo(3L);
            assertThat(resource.getVisibilityScope()).isEqualTo(CatalogVisibilityScope.DEPARTMENTS);
            assertThat(resource.getVisibleNamespaceIds()).containsExactly(29L);
            assertThat(resource.getOwnerId()).isEqualTo("feishu:bv-owner");
            assertThat(resource.getSourceKey()).startsWith("bingo-voyage-tools:");
            assertThat(resource.getDocumentation())
                    .contains("## 使用方法")
                    .contains("## 使用前准备")
                    .contains("## 访问与支持");
        });
        assertThat(saved).extracting(CatalogResource::getName)
                .contains("BV 人员排期表", "BV Prod GM 后台", "BV OpenSearch 后台", "BV SVN 资源")
                .doesNotContain("Portal 介绍", "模块接入示例");
        assertThat(saved)
                .filteredOn(resource -> resource.getSlug().equals("bv-prod-gm"))
                .singleElement()
                .extracting(CatalogResource::getDocumentation)
                .asString()
                .contains("这是生产环境入口")
                .contains("仅在获得相应授权并完成审批后执行写操作");
    }
}
