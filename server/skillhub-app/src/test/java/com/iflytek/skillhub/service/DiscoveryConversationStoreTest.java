package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.DiscoveryAiProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class DiscoveryConversationStoreTest {

    @Test
    void loadsAndAppendsAnExpiringBoundedConversation() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, String> listOperations = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(anyString(), org.mockito.ArgumentMatchers.eq(0L),
                org.mockito.ArgumentMatchers.eq(-1L)))
                .thenReturn(List.of("{\"question\":\"写周报\",\"answer\":\"先整理进展\"}"));
        DiscoveryAiProperties properties = new DiscoveryAiProperties();
        properties.setConversationMaxTurns(2);
        properties.setConversationTtlMinutes(15);
        DiscoveryConversationStore store = new DiscoveryConversationStore(
                redisTemplate, new ObjectMapper(), properties);

        DiscoveryConversationStore.Conversation conversation = store.load(
                "user-1", "0f40ad3f-7ce2-4bbb-89ec-63080a7f0648");
        store.append("user-1", conversation.id(),
                new DiscoveryConversationTurn("再精简一点", "可以压缩成三段"));

        assertThat(conversation.turns()).containsExactly(
                new DiscoveryConversationTurn("写周报", "先整理进展"));
        verify(listOperations).rightPush(contains(conversation.id()),
                org.mockito.ArgumentMatchers.contains("再精简一点"));
        verify(listOperations).trim(contains(conversation.id()), eq(-2L), eq(-1L));
        verify(redisTemplate).expire(contains(conversation.id()), eq(Duration.ofMinutes(15)));
    }

    @Test
    void createsAConversationIdWhenStartingFresh() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, String> listOperations = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        DiscoveryConversationStore store = new DiscoveryConversationStore(
                redisTemplate, new ObjectMapper(), new DiscoveryAiProperties());

        DiscoveryConversationStore.Conversation conversation = store.load("user-1", null);

        assertThat(conversation.id()).matches(
                "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
        assertThat(conversation.turns()).isEmpty();
    }
}
