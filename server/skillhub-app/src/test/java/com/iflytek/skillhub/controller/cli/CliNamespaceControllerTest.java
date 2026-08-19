package com.iflytek.skillhub.controller.cli;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.PublishTargetResponse;
import com.iflytek.skillhub.service.PublishTargetQueryAppService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CliNamespaceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired NamespaceMemberRepository namespaceMemberRepository;
    @MockBean PublishTargetQueryAppService publishTargetQueryAppService;
    @MockBean ApiTokenService apiTokenService;
    @MockBean UserAccountRepository userAccountRepository;
    @MockBean UserRoleBindingRepository userRoleBindingRepository;

    @Test
    void publishTargetsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/cli/v1/namespaces/publish-targets"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(publishTargetQueryAppService);
    }

    @Test
    void publishTargetsReturnsActiveNamespacesForTokenIdentity() throws Exception {
        ApiToken token = new ApiToken(
                "publisher", "cli", "sk_test", "hash", "[\"skill:publish\"]");
        UserAccount user = new UserAccount(
                "publisher", "Publisher", "publisher@example.com", "");
        Map<Long, NamespaceRole> roles = Map.of(12L, NamespaceRole.MEMBER);
        given(apiTokenService.validateToken("publish-token")).willReturn(Optional.of(token));
        given(userAccountRepository.findById("publisher")).willReturn(Optional.of(user));
        given(userRoleBindingRepository.findByUserId("publisher")).willReturn(List.of());
        namespaceMemberRepository.save(new NamespaceMember(12L, "publisher", NamespaceRole.MEMBER));
        given(publishTargetQueryAppService.list(roles, Set.of("USER"))).willReturn(List.of(
                new PublishTargetResponse(
                        12L, "data-team", "Data Team", NamespaceRole.MEMBER, Set.of("SKILL"))));

        mockMvc.perform(get("/api/cli/v1/namespaces/publish-targets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer publish-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].slug").value("data-team"))
                .andExpect(jsonPath("$.data[0].currentUserRole").value("MEMBER"));

        verify(publishTargetQueryAppService).list(roles, Set.of("USER"));
    }
}
