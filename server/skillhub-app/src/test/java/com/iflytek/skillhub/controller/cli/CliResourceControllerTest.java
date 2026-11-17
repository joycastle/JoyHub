package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.ResourceDiscoveryItem;
import com.iflytek.skillhub.repository.McpResourceQueryRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CliResourceControllerTest {
    @Autowired MockMvc mvc;
    @MockBean McpResourceQueryRepository resources;
    @MockBean ApiTokenService tokens;
    @MockBean UserAccountRepository users;
    @MockBean UserRoleBindingRepository roles;

    private void token(String scope) {
        given(tokens.validateToken("search-token")).willReturn(Optional.of(
                new ApiToken("search-user", "cli", "sk_test", "hash", "[\"" + scope + "\"]")));
        given(users.findById("search-user")).willReturn(Optional.of(new UserAccount("search-user", "Search", "search@example.com", null)));
        given(roles.findByUserId("search-user")).willReturn(List.of());
    }

    @Test
    void sharesMcpResourceIdentityWithoutInventingSkillCoordinates() throws Exception {
        token("skill:read");
        given(resources.search("search-user", "image", "ALL", 0, 20)).willReturn(new PageResponse<>(List.of(
                new ResourceDiscoveryItem("catalog:9", "RESOURCE_PACK", "Image utility", "Generate images", "OPEN", "image-utility", null, "1.0.0")), 1, 0, 20));
        mvc.perform(get("/api/cli/v1/resources/search").param("q", "image").header("Authorization", "Bearer search-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].resourceId").value("catalog:9"))
                .andExpect(jsonPath("$.data.items[0].kind").value("RESOURCE_PACK"))
                .andExpect(jsonPath("$.data.items[0].namespace").doesNotExist());
    }

    @Test
    void anonymousSearchRequiresLogin() throws Exception {
        mvc.perform(get("/api/cli/v1/resources/search")).andExpect(status().isUnauthorized());
        verifyNoInteractions(resources);
    }

    @Test
    void resourceReadScopeCanUseSharedSearch() throws Exception {
        token("resource:read");
        given(resources.search("search-user", "canvas", "ALL", 0, 20))
                .willReturn(new PageResponse<>(List.of(), 0, 0, 20));
        mvc.perform(get("/api/cli/v1/resources/search").param("q", "canvas")
                        .header("Authorization", "Bearer search-token"))
                .andExpect(status().isOk());
    }

    @Test
    void publishOnlyTokenCannotReadResources() throws Exception {
        token("skill:publish");
        mvc.perform(get("/api/cli/v1/resources/search").header("Authorization", "Bearer search-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(resources);
    }
}
