package com.iflytek.skillhub.authorization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/migration/V62__shared_oauth_authorization.sql",
        "spring.security.oauth2.client.registration.feishu.client-id=cli_test",
        "spring.security.oauth2.client.registration.feishu.client-secret=test-secret",
        "spring.security.oauth2.client.registration.feishu.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.feishu.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "spring.security.oauth2.client.registration.feishu.scope=contact:user.base:readonly",
        "spring.security.oauth2.client.provider.feishu.authorization-uri=https://accounts.feishu.cn/open-apis/authen/v1/authorize",
        "spring.security.oauth2.client.provider.feishu.token-uri=https://open.feishu.cn/open-apis/authen/v2/oauth/token",
        "spring.security.oauth2.client.provider.feishu.user-info-uri=https://open.feishu.cn/open-apis/authen/v1/user_info",
        "spring.security.oauth2.client.provider.feishu.user-name-attribute=open_id"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SharedOAuthFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserAccountRepository users;
    @Autowired org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService grants;
    @Autowired PlatformSessionService sessions;
    @MockBean StringRedisTemplate redis;
    @MockBean com.iflytek.skillhub.auth.oauth.FeishuOAuth2AccessTokenResponseClient upstreamTokens;
    @MockBean com.iflytek.skillhub.auth.oauth.CustomOAuth2UserService upstreamUsers;
    JsonNode client;
    MockHttpSession session;
    final String callback = "http://127.0.0.1:14567/callback";
    final String resource = "http://localhost:8080/mcp";
    final String verifier = "abcdefghijklmno1234567890abcdefghijklmno1234567890";
    String userId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(1L);
        client = mapper.readTree(mvc.perform(post("/oauth2/register").contentType("application/json").content(mapper.writeValueAsString(Map.of(
                "client_name", "OAuth integration", "redirect_uris", List.of(callback),
                "scope", "resource:read skill:read offline_access", "token_endpoint_auth_method", "client_secret_post"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        userId = "oauth-" + UUID.randomUUID();
        users.save(new UserAccount(userId, "OAuth Test", userId + "@example.test", null));
        MockHttpServletRequest request = new MockHttpServletRequest();
        sessions.establishSession(new PlatformPrincipal(userId, "OAuth Test", null, null, "test", Set.of("USER")), request);
        session = (MockHttpSession) request.getSession();
    }

    String authorize() throws Exception {
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String redirect = mvc.perform(get("/oauth2/authorize").session(session)
                .queryParam("response_type", "code").queryParam("client_id", client.get("client_id").asText())
                .queryParam("redirect_uri", callback).queryParam("scope", "resource:read skill:read offline_access")
                .queryParam("resource", resource).queryParam("state", "original-client-state").queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256")).andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertThat(redirect).startsWith(callback + "?");
        assertThat(redirect).doesNotContain("/oauth2/consent");
        var result = UriComponentsBuilder.fromUriString(redirect).build().getQueryParams();
        assertThat(result.getFirst("state")).isEqualTo("original-client-state");
        assertThat(result.getFirst("code")).isNotBlank();
        return org.springframework.web.util.UriUtils.decode(result.getFirst("code"), StandardCharsets.UTF_8);
    }

    JsonNode exchange(String code) throws Exception {
        var request = post("/oauth2/token").contentType("application/x-www-form-urlencoded")
                .param("grant_type", "authorization_code").param("code", code).param("code_verifier", verifier)
                .param("redirect_uri", callback).param("resource", resource).param("client_id", client.get("client_id").asText());
        if (client.has("client_secret")) request.param("client_secret", client.get("client_secret").asText());
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    JsonNode registerPublicClient() throws Exception {
        return mapper.readTree(mvc.perform(post("/oauth2/register").contentType("application/json").content(mapper.writeValueAsString(Map.of(
                "client_name", "Native MCP", "redirect_uris", List.of(callback),
                "scope", "resource:read skill:read offline_access", "token_endpoint_auth_method", "none"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void authorizationRefreshAndRevocationShareCliIdentity() throws Exception {
        String code = authorize();
        JsonNode token = exchange(code);
        assertThat(token.get("access_token").asText()).startsWith("jh_oauth_");
        assertThat(token.get("refresh_token").asText()).isNotBlank();
        var grant = grants.findByToken(token.get("access_token").asText(), org.springframework.security.oauth2.server.authorization.OAuth2TokenType.ACCESS_TOKEN);
        assertThat(grant).isNotNull();
        assertThat(grant.getPrincipalName()).isEqualTo(userId);
        assertThat(grant.getAccessToken().isActive()).isTrue();
        assertThat(grant.getAccessToken().getClaims().get("iss").toString()).isEqualTo("http://localhost:8080");
        assertThat(grant.getAccessToken().getClaims().get("aud")).isEqualTo(List.of(resource));
        mvc.perform(get("/api/cli/v1/auth/whoami").header("Authorization", "Bearer " + token.get("access_token").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.handle").value(userId));
        JsonNode refreshed = mapper.readTree(mvc.perform(post("/oauth2/token").contentType("application/x-www-form-urlencoded")
                .param("grant_type", "refresh_token").param("refresh_token", token.get("refresh_token").asText())
                .param("resource", resource).param("client_id", client.get("client_id").asText())
                .param("client_secret", client.get("client_secret").asText())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(refreshed.get("refresh_token").asText()).isNotEqualTo(token.get("refresh_token").asText());
        mvc.perform(get("/api/cli/v1/auth/whoami").header("Authorization", "Bearer " + token.get("access_token").asText()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/oauth2/revoke").contentType("application/x-www-form-urlencoded")
                .param("token", refreshed.get("refresh_token").asText()).param("token_type_hint", "refresh_token")
                .param("client_id", client.get("client_id").asText()).param("client_secret", client.get("client_secret").asText()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/cli/v1/auth/whoami").header("Authorization", "Bearer " + refreshed.get("access_token").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authorizationCodeCannotBeReplayed() throws Exception {
        String code = authorize();
        exchange(code);
        mvc.perform(post("/oauth2/token").contentType("application/x-www-form-urlencoded")
                .param("grant_type", "authorization_code").param("code", code).param("code_verifier", verifier)
                .param("redirect_uri", callback).param("client_id", client.get("client_id").asText())
                .param("client_secret", client.get("client_secret").asText())).andExpect(status().isBadRequest());
    }

    @Test
    void loggedInClientReturnsDirectlyToCallbackBehindHttpsProxyWithInternalHttpPort() throws Exception {
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String location = mvc.perform(get("/oauth2/authorize").session(session)
                .with(request -> {
                    request.setScheme("https");
                    request.setServerName("joyhub-proxy.internal");
                    request.setServerPort(80);
                    return request;
                })
                .queryParam("response_type", "code").queryParam("client_id", client.get("client_id").asText())
                .queryParam("redirect_uri", callback).queryParam("scope", "resource:read")
                .queryParam("resource", resource).queryParam("state", "proxy-state")
                .queryParam("code_challenge", challenge).queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertThat(location).startsWith(callback + "?");
        var params = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        assertThat(params.getFirst("code")).isNotBlank();
        assertThat(params.getFirst("state")).isEqualTo("proxy-state");
        assertThat(location).doesNotContain("joyhub-proxy.internal", ":80/");
    }

    @Test
    void unauthenticatedClientMustUseExistingJoyHubLogin() throws Exception {
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String location = mvc.perform(get("/oauth2/authorize")
                .queryParam("response_type", "code").queryParam("client_id", client.get("client_id").asText())
                .queryParam("redirect_uri", callback).queryParam("scope", "resource:read")
                .queryParam("resource", resource).queryParam("state", "login-state")
                .queryParam("code_challenge", challenge).queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertThat(location).startsWith("http://localhost:8080/login?returnTo=");
        assertThat(location).doesNotContain("code=", "/oauth2/consent");
    }

    @Test
    void repeatedConnectionsReturnDirectlyWithoutConfirmation() throws Exception {
        assertThat(authorize()).isNotEqualTo(authorize());
    }

    @Test
    void rejectsWrongPkceAndWrongResource() throws Exception {
        String code = authorize();
        mvc.perform(post("/oauth2/token").contentType("application/x-www-form-urlencoded")
                .param("grant_type", "authorization_code").param("code", code).param("code_verifier", verifier + "wrong")
                .param("redirect_uri", callback).param("resource", resource).param("client_id", client.get("client_id").asText())
                .param("client_secret", client.get("client_secret").asText())).andExpect(status().isBadRequest());
        mvc.perform(get("/oauth2/authorize").session(session).queryParam("response_type", "code")
                .queryParam("client_id", client.get("client_id").asText()).queryParam("redirect_uri", callback)
                .queryParam("scope", "resource:read").queryParam("resource", "https://elsewhere.example/mcp")
                .queryParam("code_challenge", verifier).queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern(callback + "?error=invalid_request*"));
        mvc.perform(post("/mcp")).andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("resource_metadata")));
    }

    @Test
    void firstFeishuLoginCompletesMcpConnectionWithoutStartingLoginAgain() throws Exception {
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String target = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code").queryParam("client_id", client.get("client_id").asText())
                .queryParam("redirect_uri", callback).queryParam("scope", "resource:read skill:read offline_access")
                .queryParam("resource", resource).queryParam("state", "first-login-state")
                .queryParam("code_challenge", challenge).queryParam("code_challenge_method", "S256")
                .build().encode().toUriString();
        MockHttpSession anonymous = new MockHttpSession();
        mvc.perform(get(java.net.URI.create(target)).session(anonymous))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("http://localhost:8080/login?returnTo=*"));
        String providerUrl = mvc.perform(get("/oauth2/authorization/feishu").session(anonymous).param("returnTo", target))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        String providerState = org.springframework.web.util.UriUtils.decode(
                UriComponentsBuilder.fromUriString(providerUrl).build().getQueryParams().getFirst("state"), StandardCharsets.UTF_8);
        // A browser can load authentication methods while the provider login is in flight.
        mvc.perform(get("/api/v1/auth/methods").session(anonymous)).andExpect(status().isOk());
        when(upstreamTokens.getTokenResponse(any())).thenReturn(
                org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse.withToken("upstream-test-token")
                        .tokenType(org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER).expiresIn(300).build());
        var principal = new PlatformPrincipal(userId, "OAuth Test", null, null, "feishu", Set.of("USER"));
        when(upstreamUsers.loadUser(any())).thenReturn(new org.springframework.security.oauth2.core.user.DefaultOAuth2User(
                List.of(), Map.of("platformPrincipal", principal, "providerLogin", userId), "providerLogin"));
        var login = mvc.perform(get("/login/oauth2/code/feishu").session(anonymous)
                        .param("code", "upstream-test-code").param("state", providerState))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(target)).andReturn();
        MockHttpSession loggedIn = (MockHttpSession) login.getRequest().getSession(false);
        String callbackUrl = mvc.perform(get(java.net.URI.create(target)).session(loggedIn))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertThat(callbackUrl).startsWith(callback + "?");
        var result = UriComponentsBuilder.fromUriString(callbackUrl).build().getQueryParams();
        assertThat(result.getFirst("state")).isEqualTo("first-login-state");
        JsonNode token = exchange(org.springframework.web.util.UriUtils.decode(result.getFirst("code"), StandardCharsets.UTF_8));
        mvc.perform(get("/api/cli/v1/auth/whoami").header("Authorization", "Bearer " + token.get("access_token").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.handle").value(userId));
    }

    @Test
    void publicClientRefreshesExpiredAccessWithoutSessionOrClientSecret() throws Exception {
        client = registerPublicClient();
        assertThat(client.get("token_endpoint_auth_method").asText()).isEqualTo("none");
        assertThat(client.has("client_secret")).isFalse();
        JsonNode token = exchange(authorize());
        assertThat(token.get("refresh_token").asText()).isNotBlank();
        var grant = grants.findByToken(token.get("access_token").asText(), org.springframework.security.oauth2.server.authorization.OAuth2TokenType.ACCESS_TOKEN);
        var expired = new org.springframework.security.oauth2.core.OAuth2AccessToken(
                org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER, token.get("access_token").asText(),
                java.time.Instant.now().minusSeconds(3600), java.time.Instant.now().minusSeconds(1800), grant.getAuthorizedScopes());
        grants.save(org.springframework.security.oauth2.server.authorization.OAuth2Authorization.from(grant)
                .token(expired, metadata -> metadata.putAll(grant.getAccessToken().getMetadata())).build());
        mvc.perform(get("/api/cli/v1/auth/whoami").header("Authorization", "Bearer " + expired.getTokenValue()))
                .andExpect(status().isUnauthorized());
        // A different native installation cannot redeem this client's refresh token.
        JsonNode other = registerPublicClient();
        mvc.perform(post("/oauth2/token").contentType("application/x-www-form-urlencoded")
                .param("grant_type", "refresh_token").param("refresh_token", token.get("refresh_token").asText())
                .param("client_id", other.get("client_id").asText())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
        JsonNode refreshed = mapper.readTree(mvc.perform(post("/oauth2/token").contentType("application/x-www-form-urlencoded")
                .param("grant_type", "refresh_token").param("refresh_token", token.get("refresh_token").asText())
                .param("resource", resource).param("client_id", client.get("client_id").asText()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(refreshed.get("refresh_token").asText()).isNotEqualTo(token.get("refresh_token").asText());
        mvc.perform(get("/api/cli/v1/auth/whoami").header("Authorization", "Bearer " + refreshed.get("access_token").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.handle").value(userId));
        mvc.perform(post("/oauth2/token").contentType("application/x-www-form-urlencoded")
                .param("grant_type", "refresh_token").param("refresh_token", token.get("refresh_token").asText())
                .param("client_id", client.get("client_id").asText())).andExpect(status().isBadRequest());
        mvc.perform(post("/oauth2/revoke").contentType("application/x-www-form-urlencoded")
                .param("token", refreshed.get("refresh_token").asText()).param("token_type_hint", "refresh_token")
                .param("client_id", client.get("client_id").asText())).andExpect(status().isOk());
        mvc.perform(get("/api/cli/v1/auth/whoami").header("Authorization", "Bearer " + refreshed.get("access_token").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confidentialClientsStillRequireTheirSecretAndReturnJsonErrors() throws Exception {
        JsonNode token = exchange(authorize());
        mvc.perform(post("/oauth2/token").contentType("application/x-www-form-urlencoded")
                .param("grant_type", "refresh_token").param("refresh_token", token.get("refresh_token").asText())
                .param("client_id", client.get("client_id").asText()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("invalid_client"))
                .andExpect(header().doesNotExist("Location"));
        mvc.perform(post("/oauth2/token").contentType("application/x-www-form-urlencoded")
                .param("grant_type", "refresh_token").param("refresh_token", "invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("invalid_client"))
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void nativeAuthorizationCodeStillRequiresCorrectPkce() throws Exception {
        client = registerPublicClient();
        String code = authorize();
        mvc.perform(post("/oauth2/token").contentType("application/x-www-form-urlencoded")
                .param("grant_type", "authorization_code").param("code", code).param("code_verifier", verifier + "wrong")
                .param("redirect_uri", callback).param("client_id", client.get("client_id").asText()))
                .andExpect(status().isBadRequest());
    }
}
