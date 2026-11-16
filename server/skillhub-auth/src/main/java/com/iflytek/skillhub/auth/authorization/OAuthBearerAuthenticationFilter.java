package com.iflytek.skillhub.auth.authorization;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.auth.token.ApiTokenScopeService;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Resolves OAuth grants into the same live platform principal and resource permissions used by the portal. */
public class OAuthBearerAuthenticationFilter extends OncePerRequestFilter {
    public static final String AUTHENTICATED_ATTRIBUTE = "joyhub.oauth.authenticated";
    private final OAuth2AuthorizationService authorizations;
    private final UserAccountRepository users;
    private final RbacService rbac;
    private final ApiTokenScopeService scopes;
    private final JoyHubOAuthSettings settings;

    public OAuthBearerAuthenticationFilter(OAuth2AuthorizationService authorizations, UserAccountRepository users,
                                           RbacService rbac, ApiTokenScopeService scopes, JoyHubOAuthSettings settings) {
        this.authorizations = authorizations;
        this.users = users;
        this.rbac = rbac;
        this.scopes = scopes;
        this.settings = settings;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/mcp".equals(request.getRequestURI()) && !request.getRequestURI().startsWith("/api/cli/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean mcp = "/mcp".equals(request.getRequestURI());
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            if (mcp) reject(response, 401, "invalid_token");
            else chain.doFilter(request, response);
            return;
        }
        String token = header.substring(7).trim();
        if (!token.startsWith("jh_oauth_")) {
            if (mcp) reject(response, 401, "invalid_token");
            else chain.doFilter(request, response);
            return;
        }
        OAuth2Authorization grant = authorizations.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        if (grant == null) {
            // Legacy CLI tokens remain supported by the existing API-token filter.
            if (mcp) reject(response, 401, "invalid_token");
            else chain.doFilter(request, response);
            return;
        }
        var access = grant.getAccessToken();
        Map<String, Object> claims = access == null ? null : access.getClaims();
        Object audience = claims == null ? null : claims.get("aud");
        if (access == null || !access.isActive() || claims == null || !settings.issuer().equals(String.valueOf(claims.get("iss")))
                || !(audience instanceof Collection<?> audiences && audiences.contains(settings.resource()))) {
            reject(response, 401, "invalid_token");
            return;
        }
        var user = users.findById(grant.getPrincipalName()).filter(account -> account.isActive()).orElse(null);
        if (user == null) { reject(response, 401, "invalid_token"); return; }
        Set<String> allowedScopes = access.getToken().getScopes();
        boolean allowed = mcp ? allowedScopes.contains("resource:read")
                : scopes.authorize(request.getMethod(), request.getRequestURI(), allowedScopes).allowed();
        if (!mcp && "GET".equals(request.getMethod()) && request.getRequestURI().startsWith("/api/cli/v1/skills/")
                && !allowedScopes.contains("skill:read") && !allowedScopes.contains("resource:read")) allowed = false;
        if (!allowed) { reject(response, 403, "insufficient_scope"); return; }
        Set<String> roles = rbac.getUserRoleCodes(user.getId());
        PlatformPrincipal principal = new PlatformPrincipal(user.getId(), user.getDisplayName(), user.getEmail(),
                user.getAvatarUrl(), "oauth", roles);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        allowedScopes.forEach(scope -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope)));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
        request.setAttribute(AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
        request.setAttribute("joyhub.oauth.authorizationId", grant.getId());
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String error) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("WWW-Authenticate", "Bearer resource_metadata=\"" + settings.resourceMetadata()
                + "\", scope=\"resource:read\", error=\"" + error + "\"");
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + error + "\"}");
    }
}
