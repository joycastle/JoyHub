package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    void decomposesGoalAndRetrievesResourcesForEveryStep() {
        DiscoveryAiClient aiClient = mock(DiscoveryAiClient.class);
        DiscoveryKnowledgeRetriever retriever = mock(DiscoveryKnowledgeRetriever.class);
        DiscoveryConversationStore conversationStore = mock(DiscoveryConversationStore.class);
        DiscoveryAiProperties properties = new DiscoveryAiProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        DiscoverySearchPlan plan = new DiscoverySearchPlan("完成周报", List.of(
                new DiscoverySearchPlan.Step("收集项目进展", List.of("project progress collection")),
                new DiscoverySearchPlan.Step("整理并生成报告", List.of("report generation"))));
        DiscoverySuggestionResponse reportSkill = new DiscoverySuggestionResponse(
                "skill", 7L, "Documentation Writer", "Writes reports", "SKILL",
                "documentation-writer", "global", null, null, "Generate structured reports", "SKILL.md");
        List<DiscoveryConversationTurn> history = List.of(
                new DiscoveryConversationTurn("我想做周报", "可以先收集进展。"));
        when(conversationStore.load("user-1", "0f40ad3f-7ce2-4bbb-89ec-63080a7f0648"))
                .thenReturn(new DiscoveryConversationStore.Conversation(
                        "0f40ad3f-7ce2-4bbb-89ec-63080a7f0648", history));
        when(aiClient.plan(anyString(), anyString(), anyList(), anyString())).thenReturn(plan);
        when(retriever.retrieve(
                List.of("那帮我整理一下", "project progress collection"),
                principal(), Map.of(), "zh-CN"))
                .thenReturn(List.of());
        when(retriever.retrieve(
                List.of("那帮我整理一下", "report generation"),
                principal(), Map.of(), "zh-CN"))
                .thenReturn(List.of(reportSkill));
        when(aiClient.answer(anyString(), anyString(), anyList(), anyList(), anyString()))
                .thenReturn(new DiscoveryAiClient.AiAnswer("分两步完成。", "gpt-test", false, List.of(
                        new DiscoveryAiClient.StepSelection(0, List.of()),
                        new DiscoveryAiClient.StepSelection(1, List.of(
                                new DiscoveryAiClient.ResourceRef(
                                        "skill", 7L, "帮助生成结构化报告。", "打开后粘贴报告材料。"))))));
        DiscoveryAssistantAppService service = new DiscoveryAssistantAppService(
                aiClient, properties, retriever, conversationStore);

        DiscoveryAssistResponse response = service.assist(
                "那帮我整理一下", "zh-CN", "0f40ad3f-7ce2-4bbb-89ec-63080a7f0648",
                principal(), Map.of());

        assertThat(response.conversationId()).isEqualTo("0f40ad3f-7ce2-4bbb-89ec-63080a7f0648");
        assertThat(response.steps()).hasSize(2);
        assertThat(response.steps().get(0).suggestions()).isEmpty();
        assertThat(response.steps().get(1).suggestions()).singleElement()
                .satisfies(suggestion -> {
                    assertThat(suggestion.title()).isEqualTo("Documentation Writer");
                    assertThat(suggestion.description()).isEqualTo("帮助生成结构化报告。");
                });
        assertThat(response.suggestions()).singleElement()
                .extracting(DiscoverySuggestionResponse::usage)
                .isEqualTo("打开后粘贴报告材料。");
        assertThat(response.modelGenerated()).isTrue();
        verify(aiClient).plan(eq("那帮我整理一下"), eq("zh-CN"), eq(history), anyString());
        verify(aiClient).answer(
                eq("那帮我整理一下"), eq("zh-CN"), anyList(), eq(history), anyString());
        verify(retriever).retrieve(
                List.of("那帮我整理一下", "project progress collection"),
                principal(), Map.of(), "zh-CN");
        verify(retriever).retrieve(
                List.of("那帮我整理一下", "report generation"),
                principal(), Map.of(), "zh-CN");
        verify(conversationStore).append(
                eq("user-1"), eq("0f40ad3f-7ce2-4bbb-89ec-63080a7f0648"),
                eq(new DiscoveryConversationTurn("那帮我整理一下", "分两步完成。")));
    }

    private static PlatformPrincipal principal() {
        return new PlatformPrincipal("user-1", "User", "user@example.com", null, "local", Set.of("USER"));
    }
}
