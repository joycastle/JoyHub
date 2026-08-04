package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.oauth.OAuthLoginRedirectSupport;
import com.iflytek.skillhub.dto.AuthMethodResponse;
import com.iflytek.skillhub.dto.AuthProviderResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.stereotype.Service;

/**
 * Builds the Feishu OAuth login catalog exposed to the Web UI.
 */
@Service
public class AuthMethodCatalog {

    private final OAuth2ClientProperties oAuth2ClientProperties;

    public AuthMethodCatalog(OAuth2ClientProperties oAuth2ClientProperties) {
        this.oAuth2ClientProperties = oAuth2ClientProperties;
    }

    public List<AuthProviderResponse> listOAuthProviders(String returnTo) {
        String sanitizedReturnTo = OAuthLoginRedirectSupport.sanitizeReturnTo(returnTo);
        return new ArrayList<>(oAuth2ClientProperties.getRegistration().entrySet().stream()
            .filter(entry -> "feishu".equals(entry.getKey()))
            .filter(entry -> isValidOAuthProvider(entry.getValue()))
            .sorted(Comparator.comparing(entry -> entry.getKey()))
            .map(entry -> new AuthProviderResponse(
                entry.getKey(),
                entry.getValue().getClientName() != null && !entry.getValue().getClientName().isBlank()
                    ? entry.getValue().getClientName()
                    : entry.getKey(),
                buildAuthorizationUrl(entry.getKey(), sanitizedReturnTo)
            ))
            .toList());
    }

    /**
     * Checks whether an OAuth provider has a non-empty, non-placeholder client ID.
     */
    private boolean isValidOAuthProvider(OAuth2ClientProperties.Registration registration) {
        String clientId = registration.getClientId();
        if (clientId == null || clientId.isBlank()) {
            return false;
        }
        return !clientId.toLowerCase(Locale.ROOT).contains("placeholder");
    }

    public List<AuthMethodResponse> listMethods(String returnTo) {
        String sanitizedReturnTo = OAuthLoginRedirectSupport.sanitizeReturnTo(returnTo);
        List<AuthMethodResponse> methods = new ArrayList<>();

        oAuth2ClientProperties.getRegistration().entrySet().stream()
            .filter(entry -> "feishu".equals(entry.getKey()))
            .filter(entry -> isValidOAuthProvider(entry.getValue()))
            .sorted(Comparator.comparing(entry -> entry.getKey()))
            .forEach(entry -> methods.add(new AuthMethodResponse(
                "oauth-" + entry.getKey(),
                "OAUTH_REDIRECT",
                entry.getKey(),
                entry.getValue().getClientName() != null && !entry.getValue().getClientName().isBlank()
                    ? entry.getValue().getClientName()
                    : entry.getKey(),
                buildAuthorizationUrl(entry.getKey(), sanitizedReturnTo)
            )));

        return methods;
    }

    private String buildAuthorizationUrl(String registrationId, String returnTo) {
        String baseUrl = "/oauth2/authorization/" + registrationId;
        if (returnTo == null) {
            return baseUrl;
        }
        return baseUrl + "?returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
    }
}
