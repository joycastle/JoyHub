package com.iflytek.skillhub.mcp;

import com.iflytek.skillhub.repository.McpResourceQueryRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.authorization.JoyHubOAuthSettings;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.service.ResourceDownloadAppService.ResourceDownload;
import com.iflytek.skillhub.service.ResourceReference;
import com.iflytek.skillhub.service.ResourceStatsAppService;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Single-use capabilities separate file transfer from OAuth credentials and keep credentials out of model context. */
@Service
public class McpDownloadService {
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String PREFIX = "joyhub:mcp:download:";
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final McpResourceQueryRepository resources;
    private final SkillDownloadService skills;
    private final CatalogResourceRepository catalog;
    private final ObjectStorageService storage;
    private final ResourceStatsAppService stats;
    private final OAuth2AuthorizationService grants;
    private final JoyHubOAuthSettings settings;
    private final SecureRandom random = new SecureRandom();

    public McpDownloadService(StringRedisTemplate redis, ObjectMapper mapper, McpResourceQueryRepository resources,
            SkillDownloadService skills, CatalogResourceRepository catalog, ObjectStorageService storage,
            ResourceStatsAppService stats, OAuth2AuthorizationService grants, JoyHubOAuthSettings settings) {
        this.redis = redis; this.mapper = mapper; this.resources = resources; this.skills = skills;
        this.catalog = catalog; this.storage = storage; this.stats = stats; this.grants = grants; this.settings = settings;
    }

    public Map<String, Object> issue(String userId, String authorizationId, String resourceId, String expectedRevision) {
        var resource = resources.detail(userId, resourceId, false);
        if (!resource.downloadable()) throw McpResourceQueryRepository.unavailable();
        if (expectedRevision != null && !expectedRevision.equals(resource.revision())) throw changed();
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            redis.opsForValue().set(PREFIX + McpResourceQueryRepository.revision(ticket), mapper.writeValueAsString(
                    new Ticket(userId, authorizationId, resource.resourceId(), resource.version(), resource.revision())), TTL);
        } catch (java.io.IOException exception) { throw new IllegalStateException("Could not create download ticket", exception); }
        return Map.of("resourceId", resource.resourceId(), "version", resource.version() == null ? "" : resource.version(),
                "revision", resource.revision(), "downloadUrl", settings.issuer() + "/api/mcp/downloads/" + ticket,
                "expiresAt", Instant.now().plus(TTL).toString(), "singleUse", true);
    }

    @Transactional
    public ResourceDownload redeem(String ticket) {
        if (!ticket.matches("[A-Za-z0-9_-]{43}")) throw McpResourceQueryRepository.unavailable();
        String json = redis.opsForValue().getAndDelete(PREFIX + McpResourceQueryRepository.revision(ticket));
        if (json == null) throw McpResourceQueryRepository.unavailable();
        Ticket data;
        try { data = mapper.readValue(json, Ticket.class); }
        catch (java.io.IOException exception) { throw McpResourceQueryRepository.unavailable(); }
        var grant = grants.findById(data.authorizationId());
        if (grant == null || !data.userId().equals(grant.getPrincipalName()) || grant.getAccessToken() == null
                || !grant.getAccessToken().isActive()) throw McpResourceQueryRepository.unavailable();
        var resource = resources.detail(data.userId(), data.resourceId(), false);
        if (!Objects.equals(data.revision(), resource.revision()) || !Objects.equals(data.version(), resource.version())) throw changed();
        var viewer = resources.viewer(data.userId());
        if ("SKILL".equals(resource.kind())) {
            var result = skills.downloadVersion(resource.namespace(), resource.slug(), data.version(), data.userId(), viewer.namespaceRoles());
            return new ResourceDownload(result.openContent(), result.filename(), result.contentType(), result.contentLength());
        }
        var artifact = catalog.findById(ResourceReference.parse(data.resourceId()).sourceId()).orElseThrow(McpResourceQueryRepository::unavailable);
        if (!artifact.hasArtifact() || !McpResourceQueryRepository.revision(artifact.getArtifactStorageKey()).equals(data.revision())) throw changed();
        var stream = storage.getObject(artifact.getArtifactStorageKey());
        try { stats.recordDownload(data.resourceId()); }
        catch (RuntimeException exception) {
            try { stream.close(); } catch (java.io.IOException ignored) { }
            throw exception;
        }
        return new ResourceDownload(stream, artifact.getArtifactFilename(), "application/zip", artifact.getArtifactSize());
    }

    private static ResponseStatusException changed() { return new ResponseStatusException(HttpStatus.CONFLICT, "Resource changed; request a new download"); }
    public record Ticket(String userId, String authorizationId, String resourceId, String version, String revision) { }
}
