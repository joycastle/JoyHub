package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.catalog.domain.CatalogResourceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.UnifiedResourceSearchType;
import com.iflytek.skillhub.service.CatalogResourceQueryAppService;
import com.iflytek.skillhub.service.CatalogViewer;
import com.iflytek.skillhub.service.ResourceReference;
import com.iflytek.skillhub.service.UnifiedResourceSearchAppService;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** MCP read projection reuses the registry's search and visibility policies. */
@Repository
public class McpResourceQueryRepository {
    private final UnifiedResourceSearchAppService search;
    private final SkillQueryService skillQuery;
    private final SkillRepository skills;
    private final NamespaceRepository namespaces;
    private final CatalogResourceRepository catalog;
    private final CatalogResourceQueryAppService catalogQuery;
    private final NamespaceMemberRepository members;
    private final UserAccountRepository users;
    private final RbacService rbac;

    public McpResourceQueryRepository(UnifiedResourceSearchAppService search, SkillQueryService skillQuery,
            SkillRepository skills, NamespaceRepository namespaces, CatalogResourceRepository catalog,
            CatalogResourceQueryAppService catalogQuery, NamespaceMemberRepository members, UserAccountRepository users, RbacService rbac) {
        this.search = search; this.skillQuery = skillQuery; this.skills = skills; this.namespaces = namespaces;
        this.catalog = catalog; this.catalogQuery = catalogQuery; this.members = members; this.users = users; this.rbac = rbac;
    }

    public CatalogViewer viewer(String userId) {
        if (users.findById(userId).filter(user -> user.isActive()).isEmpty()) throw unavailable();
        Map<Long, NamespaceRole> roles = members.findByUserId(userId).stream().collect(Collectors.toMap(
                member -> member.getNamespaceId(), member -> member.getRole(), (left, right) -> left));
        return new CatalogViewer(userId, roles, rbac.getUserRoleCodes(userId));
    }

    @Transactional(readOnly = true)
    public Object search(String userId, String query, String type, int page, int size) {
        CatalogViewer viewer = viewer(userId);
        if (query.length() > 1000 || page < 0 || page > 10000 || size < 1 || size > 50) throw invalid();
        var result = search.search(query, null, null, "relevance", UnifiedResourceSearchType.valueOf(type), false,
                page, size, userId, viewer.namespaceRoles(), viewer);
        var items = result.items().stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("resourceId", item.skill() != null ? "skill:" + item.skill().id() : "catalog:" + item.catalogResource().id());
            row.put("kind", item.skill() != null ? "SKILL" : item.catalogResource().kind());
            row.put("name", item.skill() != null ? item.skill().displayName() : item.catalogResource().name());
            row.put("summary", item.skill() != null ? item.skill().summary() : item.catalogResource().summary());
            row.put("accessMode", item.accessMode());
            return row;
        }).toList();
        return Map.of("items", items, "total", result.total(), "page", result.page(), "size", result.size());
    }

    @Transactional(readOnly = true)
    public Resource detail(String userId, String resourceId, boolean includeDocumentation) {
        CatalogViewer viewer = viewer(userId);
        ResourceReference ref = ResourceReference.parse(resourceId);
        if ("SKILL".equals(ref.sourceType())) {
            var skill = skills.findById(ref.sourceId()).orElseThrow(McpResourceQueryRepository::unavailable);
            String namespace = namespaces.findById(skill.getNamespaceId()).orElseThrow(McpResourceQueryRepository::unavailable).getSlug();
            var detail = skillQuery.getSkillDetail(namespace, skill.getSlug(), userId, viewer.namespaceRoles());
            if (!detail.id().equals(skill.getId())) throw unavailable();
            var version = skillQuery.resolveVersion(namespace, skill.getSlug(), null, null, null, userId, viewer.namespaceRoles());
            String documentation = "";
            if (includeDocumentation) {
                try (InputStream stream = skillQuery.getFileContent(namespace, skill.getSlug(), version.version(), "SKILL.md", userId, viewer.namespaceRoles())) {
                    documentation = new String(stream.readNBytes(128 * 1024), StandardCharsets.UTF_8);
                } catch (java.io.IOException exception) { throw unavailable(); }
            }
            return new Resource(ref.value(), "SKILL", skill.getDisplayName(), skill.getSummary(), version.version(),
                    version.fingerprint(), namespace, skill.getSlug(), documentation, null, true);
        }
        if ("CATALOG".equals(ref.sourceType())) {
            var resource = catalog.findById(ref.sourceId()).orElseThrow(McpResourceQueryRepository::unavailable);
            var detail = catalogQuery.detail(resource.getSlug(), viewer);
            return new Resource(ref.value(), detail.kind(), detail.name(), detail.summary(), detail.version(),
                    revision(resource.getArtifactStorageKey() == null ? detail.updatedAt().toString() : resource.getArtifactStorageKey()),
                    null, detail.slug(), includeDocumentation ? detail.documentation() : null, detail.accessUrl(), detail.artifactAvailable());
        }
        throw invalid();
    }

    public static String revision(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public static ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource unavailable or access denied"); }
    public static ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resource request"); }

    public record Resource(String resourceId, String kind, String name, String summary, String version, String revision,
                           String namespace, String slug, String documentation, String accessUrl, boolean downloadable) { }
}
