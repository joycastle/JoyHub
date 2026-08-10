package com.iflytek.skillhub.auth.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;

class AgentJwtAuthenticationFilterTest {

    private static final String SECRET = "test-agent-jwt-secret";
    private static final String ISSUER = "hermes-agent";
    private static final String USER_ID = "feishu:ou_kawa";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final GlobalNamespaceMembershipService membershipService =
            mock(GlobalNamespaceMembershipService.class);
    private final AuthenticationEntryPoint authenticationEntryPoint = mock(AuthenticationEntryPoint.class);
    private final AgentJwtAuthenticationFilter filter = new AgentJwtAuthenticationFilter(
            objectMapper,
            userAccountRepository,
            membershipService,
            authenticationEntryPoint,
            SECRET,
            ISSUER
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPreserveExistingDisplayNameWhenJwtOmitsName() throws Exception {
        UserAccount existingUser = new UserAccount(USER_ID, "卡瓦", null, null);
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(existingUser));
        when(userAccountRepository.save(any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authenticate(jwt(Map.of()));

        assertThat(existingUser.getDisplayName()).isEqualTo("卡瓦");
        PlatformPrincipal principal = (PlatformPrincipal) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        assertThat(principal.displayName()).isEqualTo("卡瓦");
        verify(userAccountRepository).save(existingUser);
        verify(membershipService).ensureMember(USER_ID);
    }

    @Test
    void shouldUseUserIdForNewUserWhenJwtOmitsName() throws Exception {
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authenticate(jwt(Map.of()));

        verify(userAccountRepository).save(any(UserAccount.class));
        PlatformPrincipal principal = (PlatformPrincipal) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        assertThat(principal.displayName()).isEqualTo(USER_ID);
    }

    @Test
    void shouldRefreshExistingDisplayNameWhenJwtProvidesName() throws Exception {
        UserAccount existingUser = new UserAccount(USER_ID, "旧昵称", null, null);
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(existingUser));
        when(userAccountRepository.save(any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authenticate(jwt(Map.of("name", "卡瓦")));

        assertThat(existingUser.getDisplayName()).isEqualTo("卡瓦");
    }

    private void authenticate(String jwt) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/v1/skills/search");
        request.addHeader("Authorization", "Bearer " + jwt);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    private String jwt(Map<String, Object> additionalClaims) throws Exception {
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", ISSUER);
        payload.put("sub", USER_ID);
        payload.put("exp", Instant.now().plusSeconds(300).getEpochSecond());
        payload.putAll(additionalClaims);

        String signingInput = encode(header) + "." + encode(payload);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        return signingInput + "." + signature;
    }

    private String encode(Map<String, Object> value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(value));
    }
}
