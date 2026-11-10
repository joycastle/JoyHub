package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.iflytek.skillhub.dto.CatalogResourceSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.dto.UnifiedResourceSearchType;
import com.iflytek.skillhub.search.HashingSearchEmbeddingService;
import com.iflytek.skillhub.search.HybridResourceSearchRanker;
import com.iflytek.skillhub.search.ResourceSearchQueryInterpreter;
import com.iflytek.skillhub.search.SearchTextTokenizer;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentJpaRepository;
import com.iflytek.skillhub.infra.jpa.ResourceCategoryCode;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class UnifiedResourceSearchAppServiceTest {
    @Mock
    private SkillSearchAppService skillSearchAppService;
    @Mock
    private CatalogResourceQueryAppService catalogSearchAppService;
    @Mock
    private ResourceFavoriteAppService favoriteAppService;
    @Mock
    private ResourceSearchDocumentJpaRepository searchDocumentRepository;

    private UnifiedResourceSearchAppService service;
    private CatalogViewer viewer;

    @BeforeEach
    void setUp() {
        service = new UnifiedResourceSearchAppService(
                skillSearchAppService,
                catalogSearchAppService,
                favoriteAppService,
                new HybridResourceSearchRanker(
                        new HashingSearchEmbeddingService(),
                        new ResourceSearchQueryInterpreter(new SearchTextTokenizer())),
                searchDocumentRepository,
                new ObjectMapper());
        viewer = new CatalogViewer("user-1", Map.of(), Set.of("USER"));
    }

    @Test
    void searchRanksEveryResourceTypeInOnePoolAndRejectsUnrelatedTools() {
        given(skillSearchAppService.searchInstallableLatest(
                null, null, "newest", 0, 500, List.of(), "user-1", Map.of()))
                .willReturn(new SkillSearchAppService.SearchResponse(
                        List.of(skill(1L, "html-report", "每周HTML报告生成",
                                "把表格数据生成响应式HTML报告。")), 1, 0, 500));
        given(catalogSearchAppService.search(
                eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(viewer), any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(
                        catalog(2L, "wangzong", "AGENT", "通用王总", "生成图片与多格式报告。"),
                        catalog(3L, "build-package", "ONLINE_TOOL", "构建包", "游戏构建包下载入口。"),
                        catalog(4L, "atlas-unpacker", "ONLINE_TOOL", "图集拆分", "图集拆分和资源提取工具。")
                ), 3, 0, 500));

        var result = service.search(
                "生成报告", null, null, "relevance", UnifiedResourceSearchType.ALL,
                false, 0, 12, "user-1", Map.of(), viewer);

        assertThat(result.items())
                .extracting(item -> item.resourceType() + ":" + (item.skill() != null
                        ? item.skill().slug()
                        : item.catalogResource().slug()))
                .containsExactlyInAnyOrder("SKILL:html-report", "AGENT:wangzong");
        assertThat(result.total()).isEqualTo(2);
    }

    @Test
    void toolScopeSkipsSkillCandidatesAndAppliesCatalogScopeBeforeRanking() {
        given(catalogSearchAppService.search(
                eq(null), eq("TOOL"), eq(null), eq(null), eq(null), eq(null), eq(viewer), any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(
                        catalog(3L, "atlas-unpacker", "ONLINE_TOOL", "图集拆分", "图集拆分和资源提取工具。")
                ), 1, 0, 500));

        var result = service.search(
                "图集拆分", null, null, "relevance", UnifiedResourceSearchType.TOOL,
                false, 0, 12, "user-1", Map.of(), viewer);

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.resourceType()).isEqualTo("TOOL");
            assertThat(item.catalogResource().slug()).isEqualTo("atlas-unpacker");
        });
        verify(skillSearchAppService, never()).searchInstallableLatest(
                any(), any(), any(), any(Integer.class), any(Integer.class), any(), any(), any());
    }

    @Test
    void emptyQueryCanSortTheUnifiedPoolWithoutMutatingAnImmutableList() {
        given(skillSearchAppService.searchInstallableLatest(
                null, null, "newest", 0, 500, List.of(), "user-1", Map.of()))
                .willReturn(new SkillSearchAppService.SearchResponse(
                        List.of(skill(1L, "html-report", "每周HTML报告生成", "生成响应式HTML报告。")),
                        1, 0, 500));
        given(catalogSearchAppService.search(
                eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(viewer), any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(
                        catalog(2L, "wangzong", "AGENT", "通用王总", "生成图片与多格式报告。")
                ), 1, 0, 500));

        var result = service.search(
                "", null, null, "newest", UnifiedResourceSearchType.ALL,
                false, 0, 12, "user-1", Map.of(), viewer);

        assertThat(result.items()).hasSize(2);
        assertThat(result.total()).isEqualTo(2);
    }

    @Test
    void starredOnlyFiltersSkillsAgentsAndToolsInsideTheSamePool() {
        given(skillSearchAppService.searchInstallableLatest(
                null, null, "newest", 0, 500, List.of(), "user-1", Map.of()))
                .willReturn(new SkillSearchAppService.SearchResponse(
                        List.of(skill(1L, "html-report", "每周HTML报告生成", "生成报告。")),
                        1, 0, 500));
        given(catalogSearchAppService.search(
                eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(viewer), any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(
                        catalog(2L, "wangzong", "AGENT", "通用王总", "生成报告。"),
                        catalog(3L, "atlas-unpacker", "ONLINE_TOOL", "图集拆分", "拆分图集。")
                ), 2, 0, 500));
        given(favoriteAppService.findFavoriteResourceIds("user-1"))
                .willReturn(Set.of("SKILL:1", "CATALOG:2"));

        var result = service.search(
                "", null, null, "newest", UnifiedResourceSearchType.ALL,
                true, 0, 12, "user-1", Map.of(), viewer);

        assertThat(result.items())
                .extracting(item -> item.resourceType() + ":" + (item.skill() != null
                        ? item.skill().slug()
                        : item.catalogResource().slug()))
                .containsExactlyInAnyOrder("SKILL:html-report", "AGENT:wangzong");
    }

    @Test
    void searchSkillsAppliesCategoryFilterToSkillProjection() {
        ResourceSearchDocumentEntity document = ResourceSearchDocumentEntity.basic(
                "SKILL", 1L, null, "user-1", "每周HTML报告生成", "html-report", "生成报告。",
                "[]", "INSTALL", "PUBLIC", "PUBLISHED", "生成报告。", "source-hash");
        document.setAuthorCategory(ResourceCategoryCode.GAME_DEV_QA);
        given(searchDocumentRepository.findBySearchEnabledTrue()).willReturn(List.of(document));
        given(skillSearchAppService.searchInstallableLatest(
                null, null, "newest", 0, 500, List.of(), "user-1", Map.of()))
                .willReturn(new SkillSearchAppService.SearchResponse(
                        List.of(skill(1L, "html-report", "每周HTML报告生成", "生成报告。")), 1, 0, 500));

        var result = service.searchSkills(
                null, null, List.of(), "GAME_DEV_QA", "newest", 0, 12, "user-1", Map.of());

        assertThat(result.items()).singleElement().extracting(SkillSummaryResponse::slug)
                .isEqualTo("html-report");
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void searchSkillsExcludesSkillsOutsideCategoryFilter() {
        ResourceSearchDocumentEntity document = ResourceSearchDocumentEntity.basic(
                "SKILL", 1L, null, "user-1", "每周HTML报告生成", "html-report", "生成报告。",
                "[]", "INSTALL", "PUBLIC", "PUBLISHED", "生成报告。", "source-hash");
        document.setAuthorCategory(ResourceCategoryCode.OTHER);
        given(searchDocumentRepository.findBySearchEnabledTrue()).willReturn(List.of(document));
        given(skillSearchAppService.searchInstallableLatest(
                null, null, "newest", 0, 500, List.of(), "user-1", Map.of()))
                .willReturn(new SkillSearchAppService.SearchResponse(
                        List.of(skill(1L, "html-report", "每周HTML报告生成", "生成报告。")), 1, 0, 500));

        var result = service.searchSkills(
                null, null, List.of(), "GAME_DEV_QA", "newest", 0, 12, "user-1", Map.of());

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
    }

    private SkillSummaryResponse skill(Long id, String slug, String name, String summary) {
        return new SkillSummaryResponse(
                id, slug, name, summary, null, null, "PUBLIC", "ACTIVE", 0L, 0,
                BigDecimal.ZERO, 0, "global", Instant.parse("2026-08-10T00:00:00Z"),
                false, null, null, null, "PUBLISHED");
    }

    private CatalogResourceSummaryResponse catalog(Long id,
                                                    String slug,
                                                    String kind,
                                                    String name,
                                                    String summary) {
        return new CatalogResourceSummaryResponse(
                id, slug, name, summary, kind, null, "https://example.com", "1.0.0",
                null, null, "PUBLISHED", "MAINTAINED", "COMPANY",
                Set.of(), Set.of(), false, Instant.parse("2026-08-10T00:00:00Z"));
    }
}
