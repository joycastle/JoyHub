package com.iflytek.skillhub.auth.authorization;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.authentication.AuthenticationConverter;

/**
 * Identifies explicitly registered public clients for refresh and revocation.
 * Spring Authorization Server validates token possession, client binding, expiry and rotation.
 * Authorization-code exchange still uses its built-in mandatory PKCE authentication.
 */
final class PublicOAuthClientAuthentication implements AuthenticationConverter, AuthenticationProvider {
    private final RegisteredClientRepository clients;

    PublicOAuthClientAuthentication(RegisteredClientRepository clients) {
        this.clients = clients;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        boolean refresh = "/oauth2/token".equals(request.getRequestURI())
                && "refresh_token".equals(request.getParameter("grant_type"));
        boolean revoke = "/oauth2/revoke".equals(request.getRequestURI());
        if (!"POST".equals(request.getMethod()) || (!refresh && !revoke)
                || request.getHeader("Authorization") != null || request.getParameter("client_secret") != null) {
            return null;
        }
        String[] identifiers = request.getParameterValues("client_id");
        if (identifiers == null || identifiers.length != 1 || identifiers[0].isBlank()) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        return new PublicTokenClient(identifiers[0]);
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        var client = clients.findByClientId(authentication.getName());
        if (client == null || !client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)
                || !client.getClientSettings().isRequireProofKey() || client.getTokenSettings().isReuseRefreshTokens()) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        return new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return PublicTokenClient.class.isAssignableFrom(authentication);
    }

    private static final class PublicTokenClient extends OAuth2ClientAuthenticationToken {
        PublicTokenClient(String clientId) {
            super(clientId, ClientAuthenticationMethod.NONE, null, Map.of());
        }
    }
}
