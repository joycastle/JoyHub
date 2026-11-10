package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.DiscoverySuggestionResponse;
import com.iflytek.skillhub.dto.CatalogResourceSummaryResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.dto.UnifiedResourceSearchItemResponse;
import com.iflytek.skillhub.dto.UnifiedResourceSearchType;
import com.iflytek.skillhub.infra.jpa.ResourceSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.SearchTextTokenizer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DiscoveryKnowledgeRetrieverTest {
    @Test
    void rejectingSkillsDoesNotFeedSkillCandidatesBackToTheModel() {
        UnifiedResourceSearchAppService unifiedSearch = mock(UnifiedResourceSearchAppService.class);
        ResourceSearchDocumentJpaRepository documents = mock(ResourceSearchDocumentJpaRepository.class);
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "User", "user@example.com", null, "feishu", Set.of("USER"));
        CatalogViewer viewer = new CatalogViewer("user-1", Map.of(), Set.of("USER"));
        CatalogResourceSummaryResponse tool = new CatalogResourceSummaryResponse(
                33L, "ai-imagine-cli", "AI Imagine", "生成图片", "ONLINE_TOOL", null, "https://example.com", "1.0",
                null, null, "PUBLISHED", "MAINTAINED", "COMPANY", Set.of(), Set.of(), false, Instant.EPOCH);
        when(unifiedSearch.searchForDiscovery("图片生成", UnifiedResourceSearchType.ALL, 24,
                "user-1", Map.of(), viewer, Set.of()))
                .thenReturn(new PageResponse<>(List.of(
                        new UnifiedResourceSearchItemResponse("SKILL", "INSTALL", 0.9D, skill(1L, "image", "图像技能"), null),
                        new UnifiedResourceSearchItemResponse("TOOL", "OPEN", 0.8D, null, tool)), 2, 0, 24));

        var results = new DiscoveryKnowledgeRetriever(unifiedSearch, documents, new SearchTextTokenizer())
                .retrieve(List.of("图片生成"), principal, Map.of(), "zh-CN", "我想就是纯画图功能，不是这些skill");

        assertThat(results).extracting(DiscoverySuggestionResponse::id).containsExactly(33L);
    }

    @Test
    void laterQueryCanPromoteABetterMatchBeforeTheAdviserCandidateLimit() {
        UnifiedResourceSearchAppService unifiedSearch = mock(UnifiedResourceSearchAppService.class);
        ResourceSearchDocumentJpaRepository documents = mock(ResourceSearchDocumentJpaRepository.class);
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "User", "user@example.com", null, "feishu", Set.of("USER"));
        CatalogViewer viewer = new CatalogViewer("user-1", Map.of(), Set.of("USER"));
        SkillSummaryResponse weak = skill(1L, "generic", "通用能力");
        SkillSummaryResponse strong = skill(2L, "image", "图片生成");
        when(unifiedSearch.searchForDiscovery("画图", UnifiedResourceSearchType.ALL, 24, "user-1", Map.of(), viewer, Set.of()))
                .thenReturn(new PageResponse<>(List.of(
                        new UnifiedResourceSearchItemResponse("SKILL", "INSTALL", 0.2D, weak, null),
                        new UnifiedResourceSearchItemResponse("SKILL", "INSTALL", 0.3D, strong, null)), 2, 0, 24));
        when(unifiedSearch.searchForDiscovery("图片生成", UnifiedResourceSearchType.ALL, 24, "user-1", Map.of(), viewer, Set.of()))
                .thenReturn(new PageResponse<>(List.of(
                        new UnifiedResourceSearchItemResponse("SKILL", "INSTALL", 0.9D, strong, null)), 1, 0, 24));

        var results = new DiscoveryKnowledgeRetriever(unifiedSearch, documents, new SearchTextTokenizer())
                .retrieve(List.of("画图", "图片生成"), principal, Map.of(), "zh-CN", "画图");

        assertThat(results).extracting(DiscoverySuggestionResponse::id).containsExactly(2L, 1L);
    }

    private SkillSummaryResponse skill(Long id, String slug, String name) {
        return new SkillSummaryResponse(id, slug, name, name, null, null, "PUBLIC", "ACTIVE", 0L, 0,
                BigDecimal.ZERO, 0, "global", Instant.EPOCH, false, null, null, null, "PUBLISHED");
    }

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
    void retrievesAiCandidatesFromTheUnifiedResourceSearchPool() {
        UnifiedResourceSearchAppService unifiedSearch = mock(UnifiedResourceSearchAppService.class);
        ResourceSearchDocumentJpaRepository documents = mock(ResourceSearchDocumentJpaRepository.class);
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
        when(unifiedSearch.searchForDiscovery(
                "生成报告", UnifiedResourceSearchType.ALL, 24, "user-1", roles, viewer, Set.of()))
                .thenReturn(new PageResponse<>(List.of(
                        new UnifiedResourceSearchItemResponse(
                                "SKILL", "INSTALL", 0.9D, reportSkill, null)),
                        1, 0, 24));
        DiscoveryKnowledgeRetriever retriever = new DiscoveryKnowledgeRetriever(
                unifiedSearch, documents, new SearchTextTokenizer());

        var results = retriever.retrieve(List.of("生成报告"), principal, roles, "zh-CN");

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.type()).isEqualTo("skill");
            assertThat(result.id()).isEqualTo(7L);
            assertThat(result.title()).isEqualTo("报告生成");
        });
        verify(unifiedSearch).searchForDiscovery(
                "生成报告", UnifiedResourceSearchType.ALL, 24, "user-1", roles, viewer, Set.of());
    }

    @Test
    void sendsEachRequestedQueryThroughTheSameUnifiedSearchPool() {
        UnifiedResourceSearchAppService unifiedSearch = mock(UnifiedResourceSearchAppService.class);
        ResourceSearchDocumentJpaRepository documents = mock(ResourceSearchDocumentJpaRepository.class);
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "User", "user@example.com", null, "feishu", Set.of("USER"));
        CatalogViewer viewer = new CatalogViewer("user-1", Map.of(), Set.of("USER"));
        when(unifiedSearch.searchForDiscovery("我想生成一个有视频素材的报告", UnifiedResourceSearchType.ALL, 24, "user-1", Map.of(), viewer, Set.of()))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 24));
        DiscoveryKnowledgeRetriever retriever = new DiscoveryKnowledgeRetriever(
                unifiedSearch, documents, new SearchTextTokenizer());

        retriever.retrieve(
                List.of("我想生成一个有视频素材的报告"), principal, Map.of(), "zh-CN");

        verify(unifiedSearch).searchForDiscovery("我想生成一个有视频素材的报告", UnifiedResourceSearchType.ALL, 24, "user-1", Map.of(), viewer, Set.of());
    }

    @Test
    void extractsResourceTypeAndAccessModeBeforeUsingUnifiedSearch() {
        UnifiedResourceSearchAppService unifiedSearch = mock(UnifiedResourceSearchAppService.class);
        ResourceSearchDocumentJpaRepository documents = mock(ResourceSearchDocumentJpaRepository.class);
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "User", "user@example.com", null, "feishu", Set.of("USER"));
        CatalogViewer viewer = new CatalogViewer("user-1", Map.of(), Set.of("USER"));
        when(unifiedSearch.searchForDiscovery("找一个可安装的Skill", UnifiedResourceSearchType.SKILL, 24, "user-1", Map.of(), viewer, Set.of("INSTALL")))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 24));

        new DiscoveryKnowledgeRetriever(unifiedSearch, documents, new SearchTextTokenizer())
                .retrieve(List.of("找一个可安装的Skill"), principal, Map.of(), "zh-CN");

        verify(unifiedSearch).searchForDiscovery("找一个可安装的Skill", UnifiedResourceSearchType.SKILL, 24, "user-1", Map.of(), viewer, Set.of("INSTALL"));
    }

    @Test
    void prefersExplicitSkillConstraintWhenAgentIsMentionedNegatively() {
        UnifiedResourceSearchAppService unifiedSearch = mock(UnifiedResourceSearchAppService.class);
        ResourceSearchDocumentJpaRepository documents = mock(ResourceSearchDocumentJpaRepository.class);
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "User", "user@example.com", null, "feishu", Set.of("USER"));
        CatalogViewer viewer = new CatalogViewer("user-1", Map.of(), Set.of("USER"));
        when(unifiedSearch.searchForDiscovery("生成周报", UnifiedResourceSearchType.SKILL, 24, "user-1", Map.of(), viewer, Set.of()))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 24));

        new DiscoveryKnowledgeRetriever(unifiedSearch, documents, new SearchTextTokenizer())
                .retrieve(List.of("生成周报"), principal, Map.of(), "zh-CN",
                        "优先推荐 Skill，不要只推荐通用 Agent");

        verify(unifiedSearch).searchForDiscovery("生成周报", UnifiedResourceSearchType.SKILL, 24, "user-1", Map.of(), viewer, Set.of());
    }
}
