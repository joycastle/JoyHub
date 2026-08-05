package com.iflytek.skillhub.auth.oauth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Feishu uses a JSON token endpoint and wraps API responses in {@code code}/{@code msg}.
 * Spring's default form-encoded OAuth2 token client cannot exchange Feishu auth codes.
 */
@Component
public class FeishuOAuth2AccessTokenResponseClient
        implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

    private static final Logger log = LoggerFactory.getLogger(FeishuOAuth2AccessTokenResponseClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient = RestClient.create();

    @Override
    public OAuth2AccessTokenResponse getTokenResponse(OAuth2AuthorizationCodeGrantRequest authorizationGrantRequest) {
        ClientRegistration registration = authorizationGrantRequest.getClientRegistration();
        if (!"feishu".equals(registration.getRegistrationId())) {
            throw new OAuth2AuthorizationException(
                    new OAuth2Error("unsupported_provider", "Feishu token client only supports feishu", null)
            );
        }

        var authorizationExchange = authorizationGrantRequest.getAuthorizationExchange();
        var authorizationRequest = authorizationExchange.getAuthorizationRequest();
        var authorizationResponse = authorizationExchange.getAuthorizationResponse();
        String code = authorizationResponse.getCode();
        if (!StringUtils.hasText(code)) {
            throw new OAuth2AuthorizationException(
                    new OAuth2Error("invalid_grant", "Feishu authorization code is missing", null)
            );
        }

        Map<String, String> requestBody = new LinkedHashMap<>();
        requestBody.put("grant_type", "authorization_code");
        requestBody.put("client_id", registration.getClientId());
        requestBody.put("client_secret", registration.getClientSecret());
        requestBody.put("code", code);
        String redirectUri = resolveRedirectUri(
                authorizationRequest.getRedirectUri(),
                authorizationResponse.getRedirectUri(),
                registration.getRedirectUri()
        );
        requestBody.put("redirect_uri", redirectUri);
        log.debug("Exchanging Feishu auth code with redirect_uri={}", redirectUri);

        Map<String, Object> responseBody;
        try {
            responseBody = restClient.post()
                    .uri(registration.getProviderDetails().getTokenUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange((request, response) -> {
                        try {
                            return readResponseBody(response);
                        } catch (OAuth2AuthorizationException ex) {
                            throw ex;
                        } catch (IOException ex) {
                            throw new OAuth2AuthorizationException(
                                    new OAuth2Error("invalid_token_response", "Failed to parse Feishu token response", null),
                                    ex
                            );
                        }
                    });
        } catch (RestClientException ex) {
            throw new OAuth2AuthorizationException(
                    new OAuth2Error("invalid_token_response", "Failed to call Feishu token endpoint", null),
                    ex
            );
        }

        if (responseBody == null) {
            throw new OAuth2AuthorizationException(
                    new OAuth2Error("invalid_token_response", "Feishu token response is empty", null)
            );
        }

        int apiCode = toInt(responseBody.get("code"), 0);
        if (apiCode != 0) {
            String message = formatFeishuError(responseBody);
            log.warn("Feishu token exchange failed: {}", message);
            throw new OAuth2AuthorizationException(
                    new OAuth2Error("invalid_token_response", message, null)
            );
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> tokenData = responseBody.get("data") instanceof Map<?, ?> nested
                ? (Map<String, Object>) nested
                : responseBody;

        String accessToken = asString(tokenData.get("access_token"));
        if (!StringUtils.hasText(accessToken)) {
            throw new OAuth2AuthorizationException(
                    new OAuth2Error("invalid_token_response", "Feishu token response is missing access_token", null)
            );
        }

        OAuth2AccessTokenResponse.Builder builder = OAuth2AccessTokenResponse.withToken(accessToken)
                .tokenType(OAuth2AccessToken.TokenType.BEARER);

        Long expiresIn = toLong(tokenData.get("expires_in"));
        if (expiresIn != null) {
            builder.expiresIn(expiresIn);
        }

        String refreshToken = asString(tokenData.get("refresh_token"));
        if (StringUtils.hasText(refreshToken)) {
            builder.refreshToken(refreshToken);
        }

        String scope = asString(tokenData.get("scope"));
        if (StringUtils.hasText(scope)) {
            builder.scopes(Set.of(StringUtils.delimitedListToStringArray(scope, " ")));
        }

        return builder.build();
    }

    static String resolveRedirectUri(String authorizationRequestRedirectUri,
                                     String authorizationResponseRedirectUri,
                                     String registrationRedirectUri) {
        // OAuth providers require the token request to repeat the exact redirect URI sent during
        // authorization. The ClientRegistration value may still contain placeholders such as
        // {registrationId}, so the stored authorization request is the authoritative source.
        if (StringUtils.hasText(authorizationRequestRedirectUri)) {
            return authorizationRequestRedirectUri;
        }
        if (StringUtils.hasText(authorizationResponseRedirectUri)) {
            return authorizationResponseRedirectUri;
        }
        return registrationRedirectUri;
    }

    private static Map<String, Object> readResponseBody(ClientHttpResponse response) throws IOException {
        try (response) {
            Map<String, Object> body = OBJECT_MAPPER.readValue(response.getBody(), new TypeReference<>() {
            });
            if (response.getStatusCode().isError()) {
                String message = formatFeishuError(body != null ? body : Map.of());
                log.warn("Feishu token endpoint returned {}: {}", response.getStatusCode(), message);
                throw new OAuth2AuthorizationException(
                        new OAuth2Error("invalid_token_response", message, null)
                );
            }
            return body;
        }
    }

    private static String formatFeishuError(Map<String, Object> responseBody) {
        Object description = responseBody.get("error_description");
        if (description == null) {
            description = responseBody.get("msg");
        }
        Object code = responseBody.get("code");
        if (code == null) {
            code = responseBody.get("error");
        }
        if (description != null && code != null) {
            return code + ": " + description;
        }
        if (description != null) {
            return description.toString();
        }
        return String.valueOf(responseBody);
    }

    private static int toInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
