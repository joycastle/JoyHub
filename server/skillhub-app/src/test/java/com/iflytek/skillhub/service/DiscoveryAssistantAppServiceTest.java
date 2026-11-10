package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.config.DiscoveryAiProperties;
import com.iflytek.skillhub.dto.DiscoveryAssistResponse;
import com.iflytek.skillhub.dto.DiscoverySuggestionResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DiscoveryAssistantAppServiceTest {

    @Test
    void retrievesUnifiedCandidatesBeforeGeneratingAnswer() {
        DiscoveryAiClient aiClient = mock(DiscoveryAiClient.class);
        DiscoveryKnowledgeRetriever retriever = mock(DiscoveryKnowledgeRetriever.class);
        DiscoveryConversationStore conversationStore = mock(DiscoveryConversationStore.class);
        DiscoveryAiProperties properties = new DiscoveryAiProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        DiscoverySuggestionResponse reportSkill = new DiscoverySuggestionResponse(
                "skill", 7L, "Documentation Writer", "Writes reports", "SKILL",
                "documentation-writer", "global", null, null, "Generate structured reports", "SKILL.md");
        List<DiscoveryConversationTurn> history = List.of(
                new DiscoveryConversationTurn("我想做周报", "可以先收集进展。"));
        when(conversationStore.load("user-1", "0f40ad3f-7ce2-4bbb-89ec-63080a7f0648"))
                .thenReturn(new DiscoveryConversationStore.Conversation(
                        "0f40ad3f-7ce2-4bbb-89ec-63080a7f0648", history));
        when(retriever.retrieve(
                List.of("那帮我整理一下"),
                principal(), Map.of(), "zh-CN", "那帮我整理一下"))
                .thenReturn(List.of(reportSkill));
        when(aiClient.answer(anyString(), anyString(), anyList(), anyList(), anyString()))
                .thenReturn(new DiscoveryAiClient.AiAnswer("已找到合适能力。", "gpt-test", false, List.of(
                        new DiscoveryAiClient.StepSelection(0, List.of(
                                new DiscoveryAiClient.ResourceRef(
                                        "skill", 7L, "帮助生成结构化报告。", "打开后粘贴报告材料。"))))));
        DiscoveryAssistantAppService service = new DiscoveryAssistantAppService(
                aiClient, properties, retriever, conversationStore);

        DiscoveryAssistResponse response = service.assist(
                "那帮我整理一下", "zh-CN", "0f40ad3f-7ce2-4bbb-89ec-63080a7f0648",
                principal(), Map.of());

        assertThat(response.conversationId()).isEqualTo("0f40ad3f-7ce2-4bbb-89ec-63080a7f0648");
        assertThat(response.steps()).hasSize(1);
        assertThat(response.steps().get(0).suggestions()).singleElement()
                .satisfies(suggestion -> {
                    assertThat(suggestion.title()).isEqualTo("Documentation Writer");
                    assertThat(suggestion.description()).isEqualTo("帮助生成结构化报告。");
                });
        assertThat(response.suggestions()).singleElement()
                .extracting(DiscoverySuggestionResponse::usage)
                .isEqualTo("打开后粘贴报告材料。");
        assertThat(response.modelGenerated()).isTrue();
        verify(aiClient, never()).plan(anyString(), anyString(), anyList(), anyString());
        verify(aiClient).answer(
                eq("那帮我整理一下"), eq("zh-CN"), anyList(), eq(history), anyString());
        verify(retriever).retrieve(
                List.of("那帮我整理一下"),
                principal(), Map.of(), "zh-CN", "那帮我整理一下");
        verify(conversationStore).append(
                eq("user-1"), eq("0f40ad3f-7ce2-4bbb-89ec-63080a7f0648"),
                eq(new DiscoveryConversationTurn("那帮我整理一下", "已找到合适能力。")));
    }

    @Test
    void keepsTopUnifiedSearchCandidatesWhenModelRejectsEveryCandidate() {
        DiscoveryAiClient aiClient = mock(DiscoveryAiClient.class);
        DiscoveryKnowledgeRetriever retriever = mock(DiscoveryKnowledgeRetriever.class);
        DiscoveryConversationStore conversationStore = mock(DiscoveryConversationStore.class);
        DiscoveryAiProperties properties = new DiscoveryAiProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        DiscoverySuggestionResponse reportAgent = new DiscoverySuggestionResponse(
                "catalog", 9L, "通用王总", "生成多格式报告", "AGENT",
                "general-agent", null, "https://example.com/agent", null,
                "支持生成多格式报告", "对应文档");
        when(conversationStore.load("user-1", null))
                .thenReturn(new DiscoveryConversationStore.Conversation("conversation-1", List.of()));
        when(retriever.retrieve(
                List.of("我想生成一个有视频素材的报告"),
                principal(), Map.of(), "zh-CN", "我想生成一个有视频素材的报告"))
                .thenReturn(List.of(reportAgent));
        when(aiClient.answer(anyString(), anyString(), anyList(), anyList(), anyString()))
                .thenReturn(new DiscoveryAiClient.AiAnswer(
                        "暂时没有找到匹配能力。", "gpt-test", false,
                        List.of(new DiscoveryAiClient.StepSelection(0, List.of()))));
        DiscoveryAssistantAppService service = new DiscoveryAssistantAppService(
                aiClient, properties, retriever, conversationStore);

        DiscoveryAssistResponse response = service.assist(
                "我想生成一个有视频素材的报告", "zh-CN", null, principal(), Map.of());

        assertThat(response.answer()).contains("匹配了你有权限查看的能力");
        assertThat(response.suggestions()).singleElement()
                .extracting(DiscoverySuggestionResponse::title)
                .isEqualTo("通用王总");
        assertThat(response.modelGenerated()).isFalse();
    }

    @Test
    void splitsCompoundGoalAndKeepsPurposeBuiltCandidatesPerStep() {
        DiscoveryAiClient aiClient = mock(DiscoveryAiClient.class);
        DiscoveryKnowledgeRetriever retriever = mock(DiscoveryKnowledgeRetriever.class);
        DiscoveryConversationStore conversationStore = mock(DiscoveryConversationStore.class);
        DiscoveryAiProperties properties = new DiscoveryAiProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        DiscoverySuggestionResponse weeklySkill = new DiscoverySuggestionResponse(
                "skill", 10L, "周报生成", "整理周报", "SKILL",
                "weekly-report", "global", null, null, "生成周报", "搜索画像");
        DiscoverySuggestionResponse htmlSkill = new DiscoverySuggestionResponse(
                "skill", 11L, "HTML 报告", "生成网页报告", "SKILL",
                "html-report", "global", null, null, "生成 HTML 报告", "搜索画像");
        when(conversationStore.load("user-1", null))
                .thenReturn(new DiscoveryConversationStore.Conversation("conversation-2", List.of()));
        when(retriever.retrieve(List.of("我需要把本周工作记录整理成周报"), principal(), Map.of(), "zh-CN",
                "我需要把本周工作记录整理成周报，并生成可分享的图表或网页报告"))
                .thenReturn(List.of(weeklySkill));
        when(retriever.retrieve(List.of("生成可分享的图表或网页报告"), principal(), Map.of(), "zh-CN",
                "我需要把本周工作记录整理成周报，并生成可分享的图表或网页报告"))
                .thenReturn(List.of(htmlSkill));
        when(aiClient.answer(anyString(), anyString(), anyList(), anyList(), anyString()))
                .thenReturn(new DiscoveryAiClient.AiAnswer("分两步完成。", "gpt-test", false, List.of(
                        new DiscoveryAiClient.StepSelection(0, List.of(
                                new DiscoveryAiClient.ResourceRef("skill", 10L, "整理周报", "安装后导入记录"))),
                        new DiscoveryAiClient.StepSelection(1, List.of(
                                new DiscoveryAiClient.ResourceRef("skill", 11L, "生成网页报告", "安装后导入周报"))))));

        DiscoveryAssistResponse response = new DiscoveryAssistantAppService(
                aiClient, properties, retriever, conversationStore).assist(
                "我需要把本周工作记录整理成周报，并生成可分享的图表或网页报告。",
                "zh-CN", null, principal(), Map.of());

        assertThat(response.steps()).hasSize(2);
        assertThat(response.steps().get(0).suggestions()).singleElement()
                .extracting(DiscoverySuggestionResponse::title).isEqualTo("周报生成");
        assertThat(response.steps().get(1).suggestions()).singleElement()
                .extracting(DiscoverySuggestionResponse::title).isEqualTo("HTML 报告");
    }

    @Test
    void refinementTurnReusesPreviousGoalAndAppliesLatestSkillPreference() {
        DiscoveryAiClient aiClient = mock(DiscoveryAiClient.class);
        DiscoveryKnowledgeRetriever retriever = mock(DiscoveryKnowledgeRetriever.class);
        DiscoveryConversationStore conversationStore = mock(DiscoveryConversationStore.class);
        DiscoveryAiProperties properties = new DiscoveryAiProperties();
        properties.setEnabled(false);
        List<DiscoveryConversationTurn> history = List.of(new DiscoveryConversationTurn(
                "整理工作记录生成周报，并生成网页报告", "先使用通用 Agent。"));
        when(conversationStore.load("user-1", "conversation-3"))
                .thenReturn(new DiscoveryConversationStore.Conversation("conversation-3", history));
        when(retriever.retrieve(List.of("整理工作记录生成周报"), principal(), Map.of(), "zh-CN",
                "请拆成至少两步，优先推荐 Skill，不要只推荐通用 Agent"))
                .thenReturn(List.of());
        when(retriever.retrieve(List.of("生成网页报告"), principal(), Map.of(), "zh-CN",
                "请拆成至少两步，优先推荐 Skill，不要只推荐通用 Agent"))
                .thenReturn(List.of());

        DiscoveryAssistResponse response = new DiscoveryAssistantAppService(
                aiClient, properties, retriever, conversationStore).assist(
                "请拆成至少两步，优先推荐 Skill，不要只推荐通用 Agent。",
                "zh-CN", "conversation-3", principal(), Map.of());

        assertThat(response.steps()).extracting(step -> step.objective())
                .containsExactly("整理工作记录生成周报", "生成网页报告");
    }

    private static PlatformPrincipal principal() {
        return new PlatformPrincipal("user-1", "User", "user@example.com", null, "local", Set.of("USER"));
    }
}
