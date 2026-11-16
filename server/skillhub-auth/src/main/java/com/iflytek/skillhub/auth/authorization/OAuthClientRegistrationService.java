package com.iflytek.skillhub.auth.authorization;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** RFC 7591 registration: every installation receives its own client credential and requires user consent. */
@Service
public class OAuthClientRegistrationService {
    private static final Set<String> SCOPES = Set.of("resource:read", "skill:read", "skill:publish", "offline_access");
    private final RegisteredClientRepository clients;
    private final PasswordEncoder passwords;
    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public OAuthClientRegistrationService(RegisteredClientRepository clients, PasswordEncoder passwords, StringRedisTemplate redis) {
        this.clients = clients;
        this.passwords = passwords;
        this.redis = redis;
    }

    public Map<String, Object> register(Registration request, String sourceAddress) {
        String key = "joyhub:oauth:registrations:" + sourceAddress;
        Long count = redis.opsForValue().increment(key);
        if (Long.valueOf(1).equals(count)) redis.expire(key, Duration.ofHours(1));
        if (count == null || count > 100) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        validate(request);
        Set<String> scopes = request.scope() == null || request.scope().isBlank()
                ? Set.of("resource:read", "offline_access") : new LinkedHashSet<>(List.of(request.scope().trim().split("\\s+")));
        if (!SCOPES.containsAll(scopes)) throw invalid("Unsupported scope");
        byte[] secretBytes = new byte[48];
        random.nextBytes(secretBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        String clientId = UUID.randomUUID().toString();
        String name = request.client_name() == null || request.client_name().isBlank() ? "MCP client" : request.client_name();
        // Authentication method is selected by the server and returned in registration metadata.
        // Per-installation secrets allow standard refresh/revocation without weakening PKCE for public clients.
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString()).clientId(clientId)
                .clientIdIssuedAt(Instant.now()).clientName(name).clientSecret(passwords.encode(secret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUris(uris -> uris.addAll(request.redirect_uris()))
                .scopes(values -> values.addAll(scopes))
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true).build())
                .tokenSettings(TokenSettings.builder().accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                        .accessTokenTimeToLive(Duration.ofMinutes(30)).authorizationCodeTimeToLive(Duration.ofMinutes(3))
                        .refreshTokenTimeToLive(Duration.ofDays(30)).reuseRefreshTokens(false).build()).build();
        clients.save(client);
        return Map.of("client_id", clientId, "client_secret", secret, "client_id_issued_at", Instant.now().getEpochSecond(),
                "client_secret_expires_at", 0, "client_name", name, "redirect_uris", request.redirect_uris(),
                "grant_types", List.of("authorization_code", "refresh_token"), "response_types", List.of("code"),
                "token_endpoint_auth_method", "client_secret_post", "scope", String.join(" ", scopes));
    }

    static void validate(Registration request) {
        if (request == null || request.redirect_uris() == null || request.redirect_uris().isEmpty()
                || request.redirect_uris().size() > 5 || request.redirect_uris().stream().anyMatch(java.util.Objects::isNull)
                || request.redirect_uris().stream().mapToInt(String::length).sum() > 900
                || (request.client_name() != null && (request.client_name().length() > 100
                || request.client_name().chars().anyMatch(Character::isISOControl)))) throw invalid("Invalid client metadata");
        if (request.grant_types() != null && !Set.of("authorization_code", "refresh_token").containsAll(request.grant_types()))
            throw invalid("Unsupported grant type");
        if (request.response_types() != null && !request.response_types().equals(List.of("code"))) throw invalid("Unsupported response type");
        if (request.token_endpoint_auth_method() != null
                && !Set.of("none", "client_secret_post", "client_secret_basic").contains(request.token_endpoint_auth_method()))
            throw invalid("Unsupported authentication method");
        for (String value : request.redirect_uris()) {
            try {
                URI uri = URI.create(value);
                boolean local = Set.of("127.0.0.1", "localhost", "[::1]").contains(uri.getHost());
                if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                        || !("https".equals(uri.getScheme()) || (local && "http".equals(uri.getScheme()))))
                    throw invalid("Redirect must use HTTPS or HTTP loopback");
            } catch (IllegalArgumentException exception) { throw invalid("Invalid redirect URI"); }
        }
    }

    private static ResponseStatusException invalid(String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }

    public record Registration(String client_name, List<String> redirect_uris, List<String> grant_types,
                               List<String> response_types, String token_endpoint_auth_method, String scope) { }
}
