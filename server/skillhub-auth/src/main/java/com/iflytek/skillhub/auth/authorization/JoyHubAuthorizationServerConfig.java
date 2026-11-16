package com.iflytek.skillhub.auth.authorization;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;

/** Protocol implementation is delegated to Spring Authorization Server, not bespoke token endpoints. */
@Configuration(proxyBeanMethods = false)
public class JoyHubAuthorizationServerConfig {
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerChain(HttpSecurity http, JoyHubOAuthSettings settings) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .authorizationEndpoint(endpoint -> endpoint.authenticationProviders(providers -> providers.forEach(provider -> {
                    if (provider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider codeProvider) {
                        codeProvider.setAuthenticationValidator(new OAuth2AuthorizationCodeRequestAuthenticationValidator()
                                .andThen(context -> {
                                    OAuth2AuthorizationCodeRequestAuthenticationToken request = context.getAuthentication();
                                    Map<String, Object> params = request.getAdditionalParameters();
                                    if (!settings.resource().equals(params.get("resource"))
                                            || !"S256".equals(params.get("code_challenge_method"))) {
                                        throw new OAuth2AuthorizationCodeRequestAuthenticationException(
                                                new OAuth2Error("invalid_request", "A matching resource and PKCE S256 are required", null), request);
                                    }
                                }));
                    }
                })))
                .authorizationServerMetadataEndpoint(endpoint -> endpoint.authorizationServerMetadataCustomizer(builder ->
                        builder.claim("registration_endpoint", settings.issuer() + "/oauth2/register")
                                .claim("token_endpoint_auth_methods_supported", List.of("client_secret_post", "client_secret_basic"))
                                .claim("scopes_supported", List.of("resource:read", "offline_access"))));
        http.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, error) -> {
            String returnTo = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
            response.sendRedirect(settings.issuer() + "/login?returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8));
        }));
        return http.build();
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(JoyHubOAuthSettings settings) {
        return AuthorizationServerSettings.builder().issuer(settings.issuer()).build();
    }

    @Bean
    RegisteredClientRepository oauthRegisteredClients(DataSource dataSource) {
        return new JdbcRegisteredClientRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    @DependsOnDatabaseInitialization
    OAuth2AuthorizationService oauthAuthorizations(DataSource dataSource, RegisteredClientRepository clients) {
        JdbcOAuth2AuthorizationService service = new JdbcOAuth2AuthorizationService(new JdbcTemplate(dataSource), clients);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
        mapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
        mapper.addMixIn(PlatformPrincipal.class, PlatformPrincipalMixin.class);
        var rowMapper = new JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper(clients);
        rowMapper.setObjectMapper(mapper);
        service.setAuthorizationRowMapper(rowMapper);
        var parametersMapper = new JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper();
        parametersMapper.setObjectMapper(mapper);
        service.setAuthorizationParametersMapper(parametersMapper);
        return service;
    }

    @Bean
    JdbcOAuth2AuthorizationConsentService oauthConsents(DataSource dataSource, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(new JdbcTemplate(dataSource), clients);
    }

    @Bean
    OAuth2TokenGenerator<? extends OAuth2Token> oauthTokenGenerator(JoyHubOAuthSettings settings, UserAccountRepository users) {
        OAuth2AccessTokenGenerator access = new OAuth2AccessTokenGenerator();
        access.setAccessTokenCustomizer(context -> {
            String userId = context.getPrincipal().getName();
            if (users.findById(userId).filter(user -> user.isActive()).isEmpty()) {
                throw new OAuth2AuthenticationException("access_denied");
            }
            OAuth2AuthorizationRequest original = context.getAuthorization() == null ? null
                    : context.getAuthorization().getAttribute(OAuth2AuthorizationRequest.class.getName());
            if (original == null || !settings.resource().equals(original.getAdditionalParameters().get("resource"))) {
                throw new OAuth2AuthenticationException("invalid_target");
            }
            OAuth2AuthorizationGrantAuthenticationToken grant = context.getAuthorizationGrant();
            Object requestedResource = grant.getAdditionalParameters().get("resource");
            if (requestedResource != null && !settings.resource().equals(requestedResource)) {
                throw new OAuth2AuthenticationException("invalid_target");
            }
            context.getClaims().subject(userId).audience(new java.util.ArrayList<>(List.of(settings.resource())));
        });
        OAuth2TokenGenerator<OAuth2Token> prefixedAccess = context -> {
            var token = access.generate(context);
            if (token == null) return null;
            return new PrefixedAccessToken(token, ((org.springframework.security.oauth2.core.ClaimAccessor) token).getClaims());
        };
        return new DelegatingOAuth2TokenGenerator(prefixedAccess, new OAuth2RefreshTokenGenerator());
    }

    private static final class PrefixedAccessToken extends org.springframework.security.oauth2.core.OAuth2AccessToken
            implements org.springframework.security.oauth2.core.ClaimAccessor {
        private final Map<String, Object> claims;
        PrefixedAccessToken(org.springframework.security.oauth2.core.OAuth2AccessToken token, Map<String, Object> claims) {
            super(token.getTokenType(), "jh_oauth_" + token.getTokenValue(), token.getIssuedAt(), token.getExpiresAt(), token.getScopes());
            this.claims = claims;
        }
        @Override public Map<String, Object> getClaims() { return claims; }
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = PrincipalSerializer.class)
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = PrincipalDeserializer.class)
    abstract static class PlatformPrincipalMixin {
        @JsonProperty("platformRoles")
        @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(as = java.util.HashSet.class)
        abstract Set<String> platformRoles();

        @JsonCreator
        PlatformPrincipalMixin(@JsonProperty("userId") String userId,
                               @JsonProperty("displayName") String displayName,
                               @JsonProperty("email") String email,
                               @JsonProperty("avatarUrl") String avatarUrl,
                               @JsonProperty("oauthProvider") String oauthProvider,
                               @JsonProperty("platformRoles") Set<String> platformRoles) { }
    }

    /** Persist a fixed principal shape; never deserialize implementation-specific collection classes. */
    static final class PrincipalSerializer extends com.fasterxml.jackson.databind.ser.std.StdSerializer<PlatformPrincipal> {
        PrincipalSerializer() { super(PlatformPrincipal.class); }
        @Override public void serialize(PlatformPrincipal value, com.fasterxml.jackson.core.JsonGenerator gen,
                                        com.fasterxml.jackson.databind.SerializerProvider provider) throws java.io.IOException {
            gen.writeStartObject(); fields(value, gen); gen.writeEndObject();
        }
        @Override public void serializeWithType(PlatformPrincipal value, com.fasterxml.jackson.core.JsonGenerator gen,
                com.fasterxml.jackson.databind.SerializerProvider provider, com.fasterxml.jackson.databind.jsontype.TypeSerializer type) throws java.io.IOException {
            var id = type.writeTypePrefix(gen, type.typeId(value, com.fasterxml.jackson.core.JsonToken.START_OBJECT));
            fields(value, gen); type.writeTypeSuffix(gen, id);
        }
        private void fields(PlatformPrincipal value, com.fasterxml.jackson.core.JsonGenerator gen) throws java.io.IOException {
            gen.writeStringField("userId", value.userId()); gen.writeStringField("displayName", value.displayName());
            gen.writeStringField("email", value.email()); gen.writeStringField("avatarUrl", value.avatarUrl());
            gen.writeStringField("oauthProvider", value.oauthProvider()); gen.writeArrayFieldStart("platformRoles");
            if (value.platformRoles() != null) for (String role : value.platformRoles()) gen.writeString(role);
            gen.writeEndArray();
        }
    }

    static final class PrincipalDeserializer extends com.fasterxml.jackson.databind.deser.std.StdDeserializer<PlatformPrincipal> {
        PrincipalDeserializer() { super(PlatformPrincipal.class); }
        @Override public PlatformPrincipal deserialize(com.fasterxml.jackson.core.JsonParser parser,
                com.fasterxml.jackson.databind.DeserializationContext context) throws java.io.IOException {
            com.fasterxml.jackson.databind.JsonNode node = parser.getCodec().readTree(parser);
            Set<String> roles = new java.util.HashSet<>();
            node.path("platformRoles").forEach(role -> roles.add(role.asText()));
            return new PlatformPrincipal(node.path("userId").asText(), node.path("displayName").asText(),
                    text(node, "email"), text(node, "avatarUrl"), text(node, "oauthProvider"), roles);
        }
        private static String text(com.fasterxml.jackson.databind.JsonNode node, String key) {
            return node.path(key).isNull() || node.path(key).isMissingNode() ? null : node.path(key).asText();
        }
    }
}
