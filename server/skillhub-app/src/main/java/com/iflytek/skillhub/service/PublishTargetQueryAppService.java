package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.dto.PublishTargetResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves the sole publish-target list used by Skills, Tools, and Agents. */
@Service
public class PublishTargetQueryAppService {
    private final NamespaceRepository namespaceRepository;

    public PublishTargetQueryAppService(NamespaceRepository namespaceRepository) {
        this.namespaceRepository = namespaceRepository;
    }

    @Transactional(readOnly = true)
    public List<PublishTargetResponse> list(Map<Long, NamespaceRole> namespaceRoles,
                                            Set<String> platformRoles) {
        Map<Long, NamespaceRole> roles = namespaceRoles == null ? Map.of() : namespaceRoles;
        boolean superAdmin = platformRoles != null && platformRoles.contains("SUPER_ADMIN");
        if (!superAdmin && roles.isEmpty()) {
            return List.of();
        }
        List<Namespace> namespaces = superAdmin
                ? namespaceRepository.findByStatus(NamespaceStatus.ACTIVE, Pageable.unpaged()).getContent()
                : namespaceRepository.findByIdIn(roles.keySet().stream().toList());
        return namespaces.stream()
                .filter(namespace -> namespace.getStatus() == NamespaceStatus.ACTIVE)
                .sorted(Comparator.comparing(Namespace::getSlug))
                .map(namespace -> PublishTargetResponse.from(namespace, roles.get(namespace.getId())))
                .toList();
    }
}
