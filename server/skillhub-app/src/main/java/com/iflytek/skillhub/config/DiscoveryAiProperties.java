package com.iflytek.skillhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "joyhub.ai")
public class DiscoveryAiProperties {
    private boolean enabled;
    private String baseUrl = "https://codex.cheergame.net";
    private String apiKey = "";
    private String model = "gpt-5.6-terra";
    private String translationModel = "Luna";
    private String fallbackModel = "";
    private String reasoningEffort = "medium";
    private int maxOutputTokens;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 90000;
    private int conversationTtlMinutes = 30;
    private int conversationMaxTurns = 6;
    private boolean documentationTranslationWarmupEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getTranslationModel() {
        return translationModel;
    }

    public void setTranslationModel(String translationModel) {
        this.translationModel = translationModel;
    }

    public String getFallbackModel() {
        return fallbackModel;
    }

    public void setFallbackModel(String fallbackModel) {
        this.fallbackModel = fallbackModel;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getConversationTtlMinutes() {
        return conversationTtlMinutes;
    }

    public void setConversationTtlMinutes(int conversationTtlMinutes) {
        this.conversationTtlMinutes = conversationTtlMinutes;
    }

    public int getConversationMaxTurns() {
        return conversationMaxTurns;
    }

    public void setConversationMaxTurns(int conversationMaxTurns) {
        this.conversationMaxTurns = conversationMaxTurns;
    }

    public boolean isDocumentationTranslationWarmupEnabled() {
        return documentationTranslationWarmupEnabled;
    }

    public void setDocumentationTranslationWarmupEnabled(boolean documentationTranslationWarmupEnabled) {
        this.documentationTranslationWarmupEnabled = documentationTranslationWarmupEnabled;
    }
}
