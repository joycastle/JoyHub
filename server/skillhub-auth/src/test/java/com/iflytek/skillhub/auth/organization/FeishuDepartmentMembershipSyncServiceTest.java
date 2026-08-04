package com.iflytek.skillhub.auth.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FeishuDepartmentMembershipSyncServiceTest {

    @Mock
    private NamespaceRepository namespaceRepository;

    @Mock
    private NamespaceMemberRepository namespaceMemberRepository;

    private FeishuDepartmentMembershipSyncService service;

    @BeforeEach
    void setUp() {
        service = new FeishuDepartmentMembershipSyncService(namespaceRepository, namespaceMemberRepository);
    }

    @Test
    void synchronize_addsCurrentDepartmentAndRemovesOnlyStaleFeishuMemberships() {
        Namespace current = departmentNamespace(10L, "od-current", "研发部");
        Namespace stale = departmentNamespace(11L, "od-stale", "旧部门");
        when(namespaceRepository.findByExternalProviderAndExternalId("feishu", "od-current"))
                .thenReturn(Optional.of(current));
        when(namespaceRepository.save(current)).thenReturn(current);
        when(namespaceRepository.findByExternalProvider("feishu")).thenReturn(List.of(current, stale));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(10L, "feishu:ou-user"))
                .thenReturn(Optional.empty());
        when(namespaceMemberRepository.save(any(NamespaceMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(namespaceMemberRepository.findByUserId("feishu:ou-user")).thenReturn(List.of(
                new NamespaceMember(1L, "feishu:ou-user", NamespaceRole.MEMBER),
                new NamespaceMember(11L, "feishu:ou-user", NamespaceRole.MEMBER)
        ));

        service.synchronize("feishu:ou-user", claims(List.of(
                Map.of("id", "od-current", "name", "平台研发部")
        )));

        ArgumentCaptor<NamespaceMember> member = ArgumentCaptor.forClass(NamespaceMember.class);
        verify(namespaceMemberRepository).save(member.capture());
        assertThat(member.getValue().getNamespaceId()).isEqualTo(10L);
        assertThat(member.getValue().getRole()).isEqualTo(NamespaceRole.MEMBER);
        assertThat(current.getDisplayName()).isEqualTo("平台研发部");
        verify(namespaceMemberRepository).deleteByNamespaceIdAndUserId(11L, "feishu:ou-user");
        verify(namespaceMemberRepository, never()).deleteByNamespaceIdAndUserId(1L, "feishu:ou-user");
    }

    @Test
    void synchronize_skipsWhenDirectoryLookupWasNotComplete() {
        OAuthClaims claims = new OAuthClaims(
                "feishu", "ou-user", null, false, "用户", Map.of()
        );

        service.synchronize("feishu:ou-user", claims);

        verifyNoInteractions(namespaceRepository, namespaceMemberRepository);
    }

    @Test
    void synchronize_usesExistingUserAsCreatorForNewDepartmentNamespace() {
        when(namespaceRepository.findByExternalProviderAndExternalId("feishu", "od-new"))
                .thenReturn(Optional.empty());
        when(namespaceRepository.save(any(Namespace.class))).thenAnswer(invocation -> {
            Namespace namespace = invocation.getArgument(0);
            ReflectionTestUtils.setField(namespace, "id", 12L);
            return namespace;
        });
        when(namespaceRepository.findByExternalProvider("feishu")).thenReturn(List.of());
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(12L, "feishu:ou-user"))
                .thenReturn(Optional.empty());
        when(namespaceMemberRepository.save(any(NamespaceMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(namespaceMemberRepository.findByUserId("feishu:ou-user")).thenReturn(List.of());

        service.synchronize("feishu:ou-user", List.of(
                new FeishuDirectoryClient.FeishuDepartment("od-new", "新部门")
        ));

        ArgumentCaptor<Namespace> namespace = ArgumentCaptor.forClass(Namespace.class);
        verify(namespaceRepository).save(namespace.capture());
        assertThat(namespace.getValue().getCreatedBy()).isEqualTo("feishu:ou-user");
        assertThat(namespace.getValue().getExternalId()).isEqualTo("od-new");
    }

    private static OAuthClaims claims(List<Map<String, String>> departments) {
        return new OAuthClaims(
                "feishu",
                "ou-user",
                null,
                false,
                "用户",
                Map.of(
                        FeishuDirectoryClient.ATTR_SYNC_COMPLETE, true,
                        FeishuDirectoryClient.ATTR_DEPARTMENTS, departments
                )
        );
    }

    private static Namespace departmentNamespace(Long id, String externalId, String name) {
        Namespace namespace = new Namespace("feishu-dept-test-" + id, name, "system");
        namespace.bindExternalIdentity("feishu", externalId);
        ReflectionTestUtils.setField(namespace, "id", id);
        return namespace;
    }
}
