package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.DiscoveryAiProperties;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DiscoveryPlannerRequestTest {
    @Test
    @SuppressWarnings("unchecked")
    void planningUsesAShortTimeoutInsteadOfTheFullAnswerBudget() throws Exception {
        DiscoveryAiProperties properties = new DiscoveryAiProperties();
        properties.setApiKey("test-key");
        properties.setReadTimeoutMs(90000);
        ObjectMapper mapper = new ObjectMapper();
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(mapper.writeValueAsString(Map.of("output", List.of(Map.of(
                "type", "message", "content", List.of(Map.of("type", "output_text", "text",
                        "{\"goal\":\"生成图片\",\"steps\":[{\"objective\":\"生成图片\",\"queries\":[\"图片生成\",\"image generation\"]}]}")))))));
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        OpenAiResponsesClient client = new OpenAiResponsesClient(properties, mapper, http);

        var plan = client.plan("画图", "zh-CN", List.of(), "safe-user");

        assertThat(plan.steps().getFirst().queries()).containsExactly("图片生成", "image generation");
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().timeout()).contains(Duration.ofSeconds(12));
    }
}
