package com.iflytek.skillhub.auth.organization;

import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Reconciles Feishu-owned department namespaces and the current user's memberships after login. */
@Service
public class FeishuDepartmentMembershipSyncService {

    static final String PROVIDER = "feishu";
    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;

    public FeishuDepartmentMembershipSyncService(
            NamespaceRepository namespaceRepository,
            NamespaceMemberRepository namespaceMemberRepository) {
        this.namespaceRepository = namespaceRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void synchronize(String userId, OAuthClaims claims) {
        if (!PROVIDER.equals(claims.provider())
                || !Boolean.TRUE.equals(claims.extra().get(FeishuDirectoryClient.ATTR_SYNC_COMPLETE))) {
            return;
        }

        reconcile(userId, parseDepartments(claims.extra().get(FeishuDirectoryClient.ATTR_DEPARTMENTS)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void synchronize(
            String userId,
            List<FeishuDirectoryClient.FeishuDepartment> departments) {
        Map<String, String> normalized = departments.stream().collect(Collectors.toMap(
                FeishuDirectoryClient.FeishuDepartment::externalId,
                FeishuDirectoryClient.FeishuDepartment::name,
                (first, ignored) -> first,
                LinkedHashMap::new
        ));
        reconcile(userId, normalized);
    }

    private void reconcile(String userId, Map<String, String> departments) {
        Set<Long> currentNamespaceIds = departments.entrySet().stream()
                .map(entry -> ensureDepartmentNamespace(entry.getKey(), entry.getValue(), userId))
                .map(Namespace::getId)
                .collect(Collectors.toSet());

        for (Long namespaceId : currentNamespaceIds) {
            namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                    .orElseGet(() -> namespaceMemberRepository.save(
                            new NamespaceMember(namespaceId, userId, NamespaceRole.MEMBER)
                    ));
        }

        Set<Long> managedNamespaceIds = namespaceRepository.findByExternalProvider(PROVIDER).stream()
                .map(Namespace::getId)
                .collect(Collectors.toSet());
        namespaceMemberRepository.findByUserId(userId).stream()
                .map(NamespaceMember::getNamespaceId)
                .filter(managedNamespaceIds::contains)
                .filter(namespaceId -> !currentNamespaceIds.contains(namespaceId))
                .forEach(namespaceId -> namespaceMemberRepository.deleteByNamespaceIdAndUserId(namespaceId, userId));
    }

    private Namespace ensureDepartmentNamespace(String externalId, String displayName, String createdBy) {
        Namespace namespace = namespaceRepository.findByExternalProviderAndExternalId(PROVIDER, externalId)
                .orElseGet(() -> {
                    Namespace created = new Namespace(slugFor(externalId), displayName, createdBy);
                    created.setType(NamespaceType.TEAM);
                    created.setDescription("由飞书通讯录自动同步");
                    created.bindExternalIdentity(PROVIDER, externalId);
                    return created;
                });
        if (!displayName.equals(namespace.getDisplayName())) {
            namespace.setDisplayName(displayName);
        }
        return namespaceRepository.save(namespace);
    }

    private static Map<String, String> parseDepartments(Object rawValue) {
        Map<String, String> departments = new LinkedHashMap<>();
        if (!(rawValue instanceof List<?> values)) {
            return departments;
        }
        for (Object rawDepartment : values) {
            if (!(rawDepartment instanceof Map<?, ?> department)) {
                continue;
            }
            String id = value(department.get("id"));
            String name = value(department.get("name"));
            if (id != null && name != null) {
                departments.put(id, name);
            }
        }
        return departments;
    }

    private static String value(Object raw) {
        return raw == null || raw.toString().isBlank() ? null : raw.toString();
    }

    private static String slugFor(String externalId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(externalId.getBytes(StandardCharsets.UTF_8));
            return "feishu-dept-" + HexFormat.of().formatHex(digest, 0, 10);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
