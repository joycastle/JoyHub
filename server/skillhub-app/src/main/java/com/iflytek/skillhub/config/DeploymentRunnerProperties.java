package com.iflytek.skillhub.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "joyhub.deployment")
public class DeploymentRunnerProperties {
    private String runnerBaseUrl;
    private String runnerToken;
    private String publicOrigin;
    private String pathPrefix;
    private Duration connectTimeout;
    private Duration readTimeout;

    public String stableUrl(String slug) {
        String origin = normalizeOrigin(publicOrigin);
        String prefix = normalizePathPrefix(pathPrefix);
        return origin + prefix + "/" + slug + "/";
    }

    private static String normalizePathPrefix(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("JOYHUB_DEPLOYMENT_PATH_PREFIX is required");
        }
        String prefix = value.trim();
        prefix = prefix.startsWith("/") ? prefix : "/" + prefix;
        prefix = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        return prefix;
    }

    private static String normalizeOrigin(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("JOYHUB_DEPLOYMENT_PUBLIC_ORIGIN is required");
        }
        String normalized = value.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    public String getRunnerBaseUrl() { return runnerBaseUrl; }
    public void setRunnerBaseUrl(String runnerBaseUrl) { this.runnerBaseUrl = runnerBaseUrl; }
    public String getRunnerToken() { return runnerToken; }
    public void setRunnerToken(String runnerToken) { this.runnerToken = runnerToken; }
    public String getPublicOrigin() { return publicOrigin; }
    public void setPublicOrigin(String publicOrigin) { this.publicOrigin = publicOrigin; }
    public String getPathPrefix() { return pathPrefix; }
    public void setPathPrefix(String pathPrefix) { this.pathPrefix = pathPrefix; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
}
