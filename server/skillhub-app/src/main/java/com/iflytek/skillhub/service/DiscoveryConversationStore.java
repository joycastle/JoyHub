package com.iflytek.skillhub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.DiscoveryAiProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Stores a small, expiring query transcript used only to resolve follow-up questions. */
@Service
public class DiscoveryConversationStore {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryConversationStore.class);
    private static final String KEY_PREFIX = "joyhub:discovery-conversation:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DiscoveryAiProperties properties;

    public DiscoveryConversationStore(StringRedisTemplate redisTemplate,
                                      ObjectMapper objectMapper,
                                      DiscoveryAiProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Conversation load(String userId, String requestedConversationId) {
        String conversationId = StringUtils.hasText(requestedConversationId)
                ? requestedConversationId : UUID.randomUUID().toString();
        String key = key(userId, conversationId);
        try {
            List<String> values = redisTemplate.opsForList().range(key, 0, -1);
            List<DiscoveryConversationTurn> turns = values == null ? List.of() : values.stream()
                    .map(this::readTurn)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            return new Conversation(conversationId, turns);
        } catch (RuntimeException exception) {
            log.warn("Could not load JoyHub discovery conversation; continuing without history");
            return new Conversation(conversationId, List.of());
        }
    }

    public void append(String userId, String conversationId, DiscoveryConversationTurn turn) {
        String key = key(userId, conversationId);
        int maxTurns = Math.max(1, properties.getConversationMaxTurns());
        Duration ttl = Duration.ofMinutes(Math.max(1, properties.getConversationTtlMinutes()));
        try {
            redisTemplate.opsForList().rightPush(key, objectMapper.writeValueAsString(turn));
            redisTemplate.opsForList().trim(key, -maxTurns, -1);
            redisTemplate.expire(key, ttl);
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Could not persist JoyHub discovery conversation; current answer remains available");
        }
    }

    private DiscoveryConversationTurn readTurn(String value) {
        try {
            return objectMapper.readValue(value, DiscoveryConversationTurn.class);
        } catch (JsonProcessingException exception) {
            log.warn("Ignoring malformed JoyHub discovery conversation turn");
            return null;
        }
    }

    private String key(String userId, String conversationId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(userId.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest, 0, 12) + ":" + conversationId;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record Conversation(String id, List<DiscoveryConversationTurn> turns) {
    }
}
