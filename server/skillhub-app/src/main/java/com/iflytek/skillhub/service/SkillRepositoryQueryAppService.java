package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.dto.SkillRepositoryResponse;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Projects active database namespaces for legacy repository-name display surfaces. */
@Service
public class SkillRepositoryQueryAppService {
    private final NamespaceRepository namespaceRepository;

    public SkillRepositoryQueryAppService(NamespaceRepository namespaceRepository) {
        this.namespaceRepository = namespaceRepository;
    }

    @Transactional(readOnly = true)
    public List<SkillRepositoryResponse> listActive() {
        return namespaceRepository.findByStatus(NamespaceStatus.ACTIVE, Pageable.unpaged()).getContent().stream()
                .sorted(Comparator
                        .comparing((Namespace namespace) -> !"global".equals(namespace.getSlug()))
                        .thenComparing(Namespace::getSlug))
                .map(namespace -> new SkillRepositoryResponse(
                        namespace.getSlug(),
                        namespace.getDisplayName(),
                        "global".equals(namespace.getSlug())))
                .toList();
    }
}
