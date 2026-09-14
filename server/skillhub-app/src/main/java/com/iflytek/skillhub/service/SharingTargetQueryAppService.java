package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.dto.SharingTargetResponse;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lists every active non-global department available for explicit sharing. */
@Service
public class SharingTargetQueryAppService {
    private final NamespaceRepository namespaceRepository;

    public SharingTargetQueryAppService(NamespaceRepository namespaceRepository) {
        this.namespaceRepository = namespaceRepository;
    }

    @Transactional(readOnly = true)
    public List<SharingTargetResponse> list() {
        return namespaceRepository.findByStatus(NamespaceStatus.ACTIVE, Pageable.unpaged()).getContent().stream()
                .filter(namespace -> namespace.getType() != NamespaceType.GLOBAL)
                .sorted(Comparator.comparing(Namespace::getDisplayName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(Namespace::getSlug))
                .map(namespace -> new SharingTargetResponse(
                        namespace.getId(), namespace.getSlug(), namespace.getDisplayName()))
                .toList();
    }
}
