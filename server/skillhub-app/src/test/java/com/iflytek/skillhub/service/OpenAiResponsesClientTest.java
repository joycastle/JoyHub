package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.infra.jpa.ResourceCategoryCode;
import org.junit.jupiter.api.Test;

class OpenAiResponsesClientTest {

    @Test
    void parsesJsonResponse() {
        String body = """
                {
                  "model": "gpt-5.6-sol",
                  "output": [{
                    "type": "message",
                    "content": [{"type": "output_text", "text": "Try Data Agent."}]
                  }]
                }
                """;

        OpenAiResponsesClient.ParsedResponse response =
                OpenAiResponsesClient.parseResponseBody(body, "gpt-5.6-sol");

        assertThat(response.text()).isEqualTo("Try Data Agent.");
        assertThat(response.model()).isEqualTo("gpt-5.6-sol");
    }

    @Test
    void acceptsCompletedResponseWithNullError() {
        String body = """
                {
                  "model": "gpt-6.1-terra",
                  "status": "completed",
                  "error": null,
                  "output": [{
                    "type": "message",
                    "content": [{"type": "output_text", "text": "{\\\"categoryCode\\\":\\\"CREATIVE_MEDIA\\\"}"}]
                  }]
                }
                """;

        OpenAiResponsesClient.ParsedResponse response =
                OpenAiResponsesClient.parseResponseBody(body, "gpt-6.1-terra");
        ResourceSearchProfile profile = OpenAiResponsesClient.parseSearchProfile(
                new ObjectMapper(), response.text());

        assertThat(response.model()).isEqualTo("gpt-6.1-terra");
        assertThat(profile.categoryCode()).isEqualTo(ResourceCategoryCode.CREATIVE_MEDIA);
    }

    @Test
    void rejectsNonNullResponseErrorEvenWhenOutputIsPresent() {
        String body = """
                {
                  "error": {"code": "model_not_found", "message": "private relay detail"},
                  "output": [{
                    "type": "message",
                    "content": [{"type": "output_text", "text": "Partial output"}]
                  }]
                }
                """;

        assertThatThrownBy(() -> OpenAiResponsesClient.parseResponseBody(body, "gpt-6.1-terra"))
                .isInstanceOf(AiRelayException.class)
                .hasMessage("AI_MODEL_NOT_FOUND")
                .hasMessageNotContaining("private relay detail");
    }

    @Test
    void parsesServerSentEventsReturnedByRelay() {
        String body = """
                event: response.created
                data: {"type":"response.created","response":{"model":"gpt-5.5"}}

                event: response.output_text.delta
                data: {"type":"response.output_text.delta","delta":"推荐"}

                event: response.output_text.done
                data: {"type":"response.output_text.done","text":"推荐数据助手。"}

                data: [DONE]
                """;

        OpenAiResponsesClient.ParsedResponse response =
                OpenAiResponsesClient.parseResponseBody(body, "gpt-5.6-sol");

        assertThat(response.text()).isEqualTo("推荐数据助手。");
        assertThat(response.model()).isEqualTo("gpt-5.5");
    }

    @Test
    void rejectsResponseWithoutOutputText() {
        assertThatThrownBy(() -> OpenAiResponsesClient.parseResponseBody(
                "{\"model\":\"gpt-5.6-sol\",\"output\":[]}", "gpt-5.6-sol"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI_NO_OUTPUT_TEXT");
    }

    @Test
    void parsesGenericGoalPlanWithBilingualQueries() {
        String output = """
                ```json
                {"goal":"生成项目报告","steps":[
                  {"objective":"收集项目进展","queries":["项目进展","project progress collection"]},
                  {"objective":"生成结构化报告","queries":["生成报告","structured report generation"]}
                ]}
                ```
                """;

        DiscoverySearchPlan plan = OpenAiResponsesClient.parseSearchPlan(
                new ObjectMapper(), output, "我想搞报告");

        assertThat(plan.goal()).isEqualTo("生成项目报告");
        assertThat(plan.steps()).hasSize(2);
        assertThat(plan.steps().get(1).queries()).containsExactly("生成报告", "structured report generation");
    }

    @Test
    void rejectsPlanWithoutUsableSteps() {
        assertThatThrownBy(() -> OpenAiResponsesClient.parseSearchPlan(
                new ObjectMapper(), "{\"goal\":\"report\",\"steps\":[]}", "report"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no usable steps");
    }

    @Test
    void parsesGroundedAnswerSelections() {
        String output = """
                {"answer":"先整理材料，再生成报告。","steps":[
                  {"index":0,"resources":[]},
                  {"index":1,"resources":[{"type":"skill","id":18,"introduction":"整理会议内容和行动项。","usage":"粘贴会议记录并生成行动项。"}]}
                ]}
                """;

        OpenAiResponsesClient.GroundedAnswer answer = OpenAiResponsesClient.parseGroundedAnswer(
                new ObjectMapper(), output);

        assertThat(answer.text()).isEqualTo("先整理材料，再生成报告。");
        assertThat(answer.selections().get(1).resources())
                .containsExactly(new DiscoveryAiClient.ResourceRef(
                        "skill", 18L, "整理会议内容和行动项。", "粘贴会议记录并生成行动项。"));
    }

    @Test
    void keepsOnePrimaryRecommendationAndUpToThreeAlternativesPerStep() {
        String output = """
                {"answer":"推荐一项首选能力和三个备选。","steps":[
                  {"index":0,"resources":[
                    {"type":"skill","id":1,"introduction":"能力一","usage":"用法一"},
                    {"type":"skill","id":2,"introduction":"能力二","usage":"用法二"},
                    {"type":"catalog","id":3,"introduction":"能力三","usage":"用法三"},
                    {"type":"catalog","id":4,"introduction":"能力四","usage":"用法四"},
                    {"type":"skill","id":5,"introduction":"能力五","usage":"用法五"}
                  ]}
                ]}
                """;

        OpenAiResponsesClient.GroundedAnswer answer = OpenAiResponsesClient.parseGroundedAnswer(
                new ObjectMapper(), output);

        assertThat(answer.selections().getFirst().resources())
                .extracting(DiscoveryAiClient.ResourceRef::id)
                .containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void parsesEverySearchProfileCategoryCode() {
        for (ResourceCategoryCode expected : ResourceCategoryCode.values()) {
            ResourceSearchProfile profile = OpenAiResponsesClient.parseSearchProfile(new ObjectMapper(),
                    "{\"categoryCode\":\"" + expected.name().toLowerCase() + "\"}");

            assertThat(profile.categoryCode()).isEqualTo(expected);
        }
    }

    @Test
    void fallsBackToOtherForMissingOrUnknownSearchProfileCategory() {
        assertThat(OpenAiResponsesClient.parseSearchProfile(new ObjectMapper(), "{}").categoryCode())
                .isEqualTo(ResourceCategoryCode.OTHER);
        assertThat(OpenAiResponsesClient.parseSearchProfile(new ObjectMapper(),
                "{\"categoryCode\":\"made_up\"}").categoryCode())
                .isEqualTo(ResourceCategoryCode.OTHER);
    }
    @Test
    void parsesFencedProfileJsonAndRejectsInvalidShapes() {
        ResourceSearchProfile profile = OpenAiResponsesClient.parseSearchProfile(new ObjectMapper(),
                "```json\n{\"categoryCode\":\"CREATIVE_MEDIA\",\"searchTerms\":[\"image generation\"]}\n```");
        assertThat(profile.categoryCode()).isEqualTo(ResourceCategoryCode.CREATIVE_MEDIA);
        assertThat(profile.searchTerms()).containsExactly("image generation");
        for (String invalid : java.util.List.of("null", "[]", "not json", "")) {
            assertThatThrownBy(() -> OpenAiResponsesClient.parseSearchProfile(new ObjectMapper(), invalid))
                    .isInstanceOf(AiRelayException.class).hasMessage("PROFILE_INVALID_JSON");
        }
    }

    @Test
    void retriesTemporaryRelayFailuresButStopsOnConfigurationAndQuotaErrors() {
        assertThat(OpenAiResponsesClient.relayHttpFailure(503, "upstream unavailable", null).retryable()).isTrue();
        AiRelayException limited = OpenAiResponsesClient.relayHttpFailure(429, "{}", "120");
        assertThat(limited.retryable()).isTrue();
        assertThat(limited.retryAfter()).isEqualTo(java.time.Duration.ofMinutes(2));
        assertThat(OpenAiResponsesClient.relayHttpFailure(401, "secret response body", null).retryable()).isFalse();
        assertThat(OpenAiResponsesClient.relayHttpFailure(400, "{}", null).retryable()).isFalse();
        AiRelayException quota = OpenAiResponsesClient.relayHttpFailure(429,
                "{\"error\":{\"code\":\"insufficient_quota\",\"message\":\"secret-key\"}}", null);
        assertThat(quota.retryable()).isFalse();
        assertThat(quota.code()).isEqualTo("AI_QUOTA_EXHAUSTED");
        assertThat(quota.getMessage()).doesNotContain("secret-key");
    }

}
