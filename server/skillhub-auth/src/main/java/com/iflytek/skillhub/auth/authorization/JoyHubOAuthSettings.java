package com.iflytek.skillhub.auth.authorization;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Canonical identifiers must never depend on an untrusted request Host header. */
@Component
public class JoyHubOAuthSettings {
    private final String issuer;

    public JoyHubOAuthSettings(@Value("${skillhub.public.base-url:}") String publicBaseUrl) {
        String value = publicBaseUrl == null || publicBaseUrl.isBlank()
                ? "http://localhost:8080" : publicBaseUrl.replaceAll("/+$", "");
        URI uri = URI.create(value);
        boolean loopback = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (uri.getHost() == null || (uri.getPath() != null && !uri.getPath().isEmpty()) || uri.getQuery() != null || uri.getFragment() != null
                || uri.getUserInfo() != null || (!"https".equals(uri.getScheme())
                && !(loopback && "http".equals(uri.getScheme())))) {
            throw new IllegalArgumentException("JoyHub OAuth requires a canonical HTTPS public base URL");
        }
        this.issuer = value;
    }

    public String issuer() { return issuer; }
    public String resource() { return issuer + "/mcp"; }
    public String resourceMetadata() { return issuer + "/.well-known/oauth-protected-resource"; }
}
