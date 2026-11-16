package com.iflytek.skillhub.mcp;

import com.iflytek.skillhub.repository.McpResourceQueryRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.authorization.JoyHubOAuthSettings;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.service.CatalogViewer;
import com.iflytek.skillhub.service.ResourceStatsAppService;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class McpDownloadServiceTest {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
    ObjectMapper mapper = new ObjectMapper();
    McpResourceQueryRepository resources = mock(McpResourceQueryRepository.class);
    SkillDownloadService skills = mock(SkillDownloadService.class);
    OAuth2AuthorizationService grants = mock(OAuth2AuthorizationService.class);
    McpDownloadService service = new McpDownloadService(redis, mapper, resources, skills, mock(CatalogResourceRepository.class),
            mock(ObjectStorageService.class), mock(ResourceStatsAppService.class), grants, new JoyHubOAuthSettings("https://joyhub.example"));
    final String ticket = "a".repeat(43);
    final McpResourceQueryRepository.Resource resource = new McpResourceQueryRepository.Resource("skill:1", "SKILL", "Test", "Summary", "1.0.0", "revision", "global", "test", null, null, true);

    @BeforeEach void setup() {
        when(redis.opsForValue()).thenReturn(values);
        when(resources.detail("user", "skill:1", false)).thenReturn(resource);
        when(resources.viewer("user")).thenReturn(new CatalogViewer("user", Map.of(), Set.of("USER")));
    }

    void validTicket() throws Exception {
        when(values.getAndDelete(anyString())).thenReturn(mapper.writeValueAsString(new McpDownloadService.Ticket("user", "grant", "skill:1", "1.0.0", "revision")), null);
        var client = RegisteredClient.withId("client").clientId("client").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("https://example/callback").build();
        when(grants.findById("grant")).thenReturn(OAuth2Authorization.withRegisteredClient(client).id("grant").principalName("user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).accessToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                        "test-only", Instant.now().minusSeconds(10), Instant.now().plusSeconds(300), Set.of("resource:read"))).build());
    }

    @Test void ticketIsPrivateShortLivedAndDoesNotContainAuthorizationToken() {
        var result = service.issue("user", "grant", "skill:1", "revision");
        assertThat(result.get("downloadUrl").toString()).startsWith("https://joyhub.example/api/mcp/downloads/");
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(values).set(key.capture(), anyString(), eq(java.time.Duration.ofMinutes(5)));
        String publicTicket = result.get("downloadUrl").toString().substring(result.get("downloadUrl").toString().lastIndexOf('/') + 1);
        assertThat(key.getValue()).doesNotContain(publicTicket);
        assertThatThrownBy(() -> service.issue("user", "grant", "skill:1", "old-revision")).isInstanceOf(ResponseStatusException.class);
    }

    @Test void downloadPinsPublishedVersionAndTicketWorksOnlyOnce() throws Exception {
        validTicket();
        SkillDownloadService.DownloadResult file = new SkillDownloadService.DownloadResult(
                () -> new ByteArrayInputStream(new byte[]{1}), "test.zip", 1L, "application/zip", null, false);
        when(skills.downloadVersion("global", "test", "1.0.0", "user", Map.of())).thenReturn(file);
        assertThat(service.redeem(ticket).filename()).isEqualTo("test.zip");
        assertThatThrownBy(() -> service.redeem(ticket)).isInstanceOf(ResponseStatusException.class);
        verify(skills, times(1)).downloadVersion(anyString(), anyString(), anyString(), anyString(), anyMap());
    }

    @Test void rejectsExpiredAndRevokedTicketsWithoutReadingResource() throws Exception {
        assertThatThrownBy(() -> service.redeem(ticket)).isInstanceOf(ResponseStatusException.class);
        validTicket();
        when(grants.findById("grant")).thenReturn(null);
        assertThatThrownBy(() -> service.redeem(ticket)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(resources, skills);
    }

    @Test void rechecksAccessAndRevisionAtRedemption() throws Exception {
        validTicket();
        when(resources.detail("user", "skill:1", false)).thenThrow(McpResourceQueryRepository.unavailable());
        assertThatThrownBy(() -> service.redeem(ticket)).isInstanceOf(ResponseStatusException.class);
        validTicket();
        doReturn(new McpResourceQueryRepository.Resource("skill:1", "SKILL", "Test", "Summary", "2.0.0", "changed", "global", "test", null, null, true)).when(resources).detail("user", "skill:1", false);
        assertThatThrownBy(() -> service.redeem(ticket)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(skills);
    }
}
