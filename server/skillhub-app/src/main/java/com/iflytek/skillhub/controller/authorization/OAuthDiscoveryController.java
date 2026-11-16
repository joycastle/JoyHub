package com.iflytek.skillhub.controller.authorization;

import com.iflytek.skillhub.auth.authorization.JoyHubOAuthSettings;
import com.iflytek.skillhub.auth.authorization.OAuthClientRegistrationService;
import io.swagger.v3.oas.annotations.Hidden;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Public discovery and client registration use OAuth wire formats rather than the portal API envelope. */
@Hidden
@RestController
public class OAuthDiscoveryController {
    private final JoyHubOAuthSettings settings;
    private final OAuthClientRegistrationService registrations;

    public OAuthDiscoveryController(JoyHubOAuthSettings settings, OAuthClientRegistrationService registrations) {
        this.settings = settings;
        this.registrations = registrations;
    }

    @GetMapping({"/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp"})
    public Map<String, Object> resourceMetadata() {
        return Map.of("resource", settings.resource(), "authorization_servers", List.of(settings.issuer()),
                "scopes_supported", List.of("resource:read"), "bearer_methods_supported", List.of("header"),
                "resource_name", "JoyHub resource registry");
    }

    @PostMapping("/oauth2/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody OAuthClientRegistrationService.Registration body,
                                                       HttpServletRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                    .body(registrations.register(body, request.getRemoteAddr()));
        } catch (ResponseStatusException exception) {
            return ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore())
                    .body(Map.of("error", "invalid_client_metadata", "error_description",
                            exception.getReason() == null ? "Client registration rejected" : exception.getReason()));
        }
    }
}
