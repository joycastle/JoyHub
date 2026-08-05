package com.iflytek.skillhub.auth.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Failure handler for OAuth logins that normalizes policy and account-state
 * failures into predictable user-facing redirects.
 */
@Component
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

    private final OAuthLoginFlowService oauthLoginFlowService;
    private final String webBaseUrl;

    public OAuth2LoginFailureHandler(OAuthLoginFlowService oauthLoginFlowService,
                                     @Value("${skillhub.auth.web-base-url:}") String webBaseUrl) {
        this.oauthLoginFlowService = oauthLoginFlowService;
        this.webBaseUrl = webBaseUrl;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception)
            throws IOException, ServletException {
        log.warn("OAuth login failed: {} - {}", exception.getClass().getSimpleName(), exception.getMessage(), exception);
        String returnTo = oauthLoginFlowService.consumeReturnTo(request.getSession(false));
        String redirectTarget = oauthLoginFlowService.resolveFailureRedirect(exception, returnTo);
        if (redirectTarget != null) {
            getRedirectStrategy().sendRedirect(
                    request,
                    response,
                    OAuthLoginRedirectSupport.toWebRedirect(redirectTarget, webBaseUrl)
            );
            return;
        }

        getRedirectStrategy().sendRedirect(
                request,
                response,
                OAuthLoginRedirectSupport.toWebRedirect("/login?reason=oauthFailed", webBaseUrl)
        );
    }
}
