package com.iflytek.skillhub.controller.portal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.dto.SkillRepositoryResponse;
import com.iflytek.skillhub.service.SkillRepositoryQueryAppService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkillRepositoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SkillRepositoryQueryAppService queryAppService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @Test
    void listRepositories_shouldAllowAnonymousSharedSkillEntry() throws Exception {
        when(queryAppService.listActive()).thenReturn(List.of(
                new SkillRepositoryResponse("global", "Global", true)));

        mockMvc.perform(get("/api/web/repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].slug").value("global"));

        mockMvc.perform(get("/api/v1/repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].defaultRepository").value(true));
    }
}
