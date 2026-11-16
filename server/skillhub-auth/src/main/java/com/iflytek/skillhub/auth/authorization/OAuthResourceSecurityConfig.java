package com.iflytek.skillhub.auth.authorization;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.auth.token.ApiTokenScopeService;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
public class OAuthResourceSecurityConfig {
    @Bean
    OAuthBearerAuthenticationFilter oauthBearerFilter(OAuth2AuthorizationService authorizations, UserAccountRepository users,
                                                      RbacService rbac, ApiTokenScopeService scopes, JoyHubOAuthSettings settings) {
        return new OAuthBearerAuthenticationFilter(authorizations, users, rbac, scopes, settings);
    }

    @Bean
    FilterRegistrationBean<OAuthBearerAuthenticationFilter> oauthFilterRegistration(OAuthBearerAuthenticationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(2)
    SecurityFilterChain mcpResourceChain(HttpSecurity http, OAuthBearerAuthenticationFilter filter) throws Exception {
        return http.securityMatcher("/mcp").csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority("SCOPE_resource:read"))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class).build();
    }
}
