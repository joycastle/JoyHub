package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.dto.UnifiedResourceSearchItemResponse;
import com.iflytek.skillhub.dto.UnifiedResourceSearchType;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.HashingSearchEmbeddingService;
import com.iflytek.skillhub.search.SearchTextTokenizer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DiscoveryKnowledgeRetrieverTest {
    @Test
    void selectsTheMostRelevantDocumentationParagraph() {
        String document = """
                # 安装
                下载插件并重新启动编辑器。

                # 数据分析
                支持 SQL 查询、经营报表、趋势分析和数据可视化。

                # 反馈
                请联系维护人提交问题。
                """;

        assertThat(DiscoveryKnowledgeRetriever.bestExcerpt("分析经营数据并生成报表", document, "fallback"))
                .contains("SQL 查询", "经营报表", "数据可视化");
    }

    @Test
    void splitsLongDocumentationIntoBoundedChunksWithSmallOverlap() {
        String document = "# 使用方法\n" + "数据分析工具支持 SQL 查询和经营报表。 ".repeat(80);

        assertThat(DiscoveryKnowledgeRetriever.chunk(document))
                .isNotEmpty()
                .allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(720));
        assertThat(DiscoveryKnowledgeRetriever.chunk(document).stream()
                .mapToInt(String::length)
                .sum()).isGreaterThan(720);
    }

    @Test
    void retrievesAiCandidatesFromTheUnifiedResourceSearchPool() {
        CatalogResourceRepository catalogRepository = mock(CatalogResourceRepository.class);
        UnifiedResourceSearchAppService unifiedSearch = mock(UnifiedResourceSearchAppService.class);
        SkillSearchDocumentJpaRepository skillDocuments = mock(SkillSearchDocumentJpaRepository.class);
        SkillSummaryResponse reportSkill = new SkillSummaryResponse(
                7L, "report-writer", "Report Writer", "Generate reports",
                "报告生成", "生成多格式报告", "PUBLIC", "ACTIVE",
                0L, 0, BigDecimal.ZERO, 0, "global",
                Instant.parse("2026-08-10T00:00:00Z"), false,
                null, null, null, "PUBLISHED");
        Map<Long, NamespaceRole> roles = Map.of(3L, NamespaceRole.MEMBER);
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "User", "user@example.com", null, "feishu", Set.of("USER"));
        CatalogViewer viewer = new CatalogViewer("user-1", roles, Set.of("USER"));
        when(unifiedSearch.search(
                "生成报告", null, null, "relevance", UnifiedResourceSearchType.ALL, false,
                0, 100, "user-1", roles, viewer))
                .thenReturn(new PageResponse<>(List.of(
                        new UnifiedResourceSearchItemResponse(
                                "SKILL", "INSTALL", 0.9D, reportSkill, null)),
                        1, 0, 100));
        DiscoveryKnowledgeRetriever retriever = new DiscoveryKnowledgeRetriever(
                catalogRepository, unifiedSearch, skillDocuments,
                new HashingSearchEmbeddingService(), new SearchTextTokenizer());

        var results = retriever.retrieve(List.of("生成报告"), principal, roles, "zh-CN");

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.type()).isEqualTo("skill");
            assertThat(result.id()).isEqualTo(7L);
            assertThat(result.title()).isEqualTo("报告生成");
        });
        verify(unifiedSearch).search(
                "生成报告", null, null, "relevance", UnifiedResourceSearchType.ALL, false,
                0, 100, "user-1", roles, viewer);
    }

    @Test
    void expandsCompoundGoalThroughTheSameUnifiedSearchPool() {
        CatalogResourceRepository catalogRepository = mock(CatalogResourceRepository.class);
        UnifiedResourceSearchAppService unifiedSearch = mock(UnifiedResourceSearchAppService.class);
        SkillSearchDocumentJpaRepository skillDocuments = mock(SkillSearchDocumentJpaRepository.class);
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "User", "user@example.com", null, "feishu", Set.of("USER"));
        CatalogViewer viewer = new CatalogViewer("user-1", Map.of(), Set.of("USER"));
        for (String query : List.of(
                "我想生成一个有视频素材的报告", "生成 报告", "视频 素材")) {
            when(unifiedSearch.search(
                    query, null, null, "relevance", UnifiedResourceSearchType.ALL, false,
                    0, 100, "user-1", Map.of(), viewer))
                    .thenReturn(new PageResponse<>(List.of(), 0, 0, 100));
        }
        DiscoveryKnowledgeRetriever retriever = new DiscoveryKnowledgeRetriever(
                catalogRepository, unifiedSearch, skillDocuments,
                new HashingSearchEmbeddingService(), new SearchTextTokenizer());

        retriever.retrieve(
                List.of("我想生成一个有视频素材的报告"), principal, Map.of(), "zh-CN");

        for (String query : List.of(
                "我想生成一个有视频素材的报告", "生成 报告", "视频 素材")) {
            verify(unifiedSearch).search(
                    query, null, null, "relevance", UnifiedResourceSearchType.ALL, false,
                    0, 100, "user-1", Map.of(), viewer);
        }
    }
}
