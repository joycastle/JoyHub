package com.iflytek.skillhub.catalog.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Entity
@Table(name = "catalog_resource")
public class CatalogResource {
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 96)
    private String slug;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 1200)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CatalogResourceKind kind;

    @Column(length = 256)
    private String icon;

    @Column(name = "access_url", length = 1024)
    private String accessUrl;

    @Column(columnDefinition = "TEXT")
    private String documentation;

    @Column(length = 64)
    private String version;

    @Column(name = "agent_usage_boundary", columnDefinition = "TEXT")
    private String agentUsageBoundary;

    @Column(name = "agent_input_guide", columnDefinition = "TEXT")
    private String agentInputGuide;

    @Column(name = "agent_output_guide", columnDefinition = "TEXT")
    private String agentOutputGuide;

    @Column(name = "agent_support_contact", length = 256)
    private String agentSupportContact;

    @Column(name = "primary_namespace_id")
    private Long primaryNamespaceId;

    @Column(name = "owner_id", nullable = false, length = 128)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CatalogResourceStatus status = CatalogResourceStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "maintenance_status", nullable = false, length = 32)
    private CatalogMaintenanceStatus maintenanceStatus = CatalogMaintenanceStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility_scope", nullable = false, length = 32)
    private CatalogVisibilityScope visibilityScope = CatalogVisibilityScope.COMPANY;

    @Column(name = "artifact_storage_key", length = 512)
    private String artifactStorageKey;

    @Column(name = "artifact_filename", length = 256)
    private String artifactFilename;

    @Column(name = "artifact_content_type", length = 160)
    private String artifactContentType;

    @Column(name = "artifact_size")
    private Long artifactSize;

    @Column(name = "source_key", unique = true, length = 160)
    private String sourceKey;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "catalog_resource_visible_namespace", joinColumns = @JoinColumn(name = "resource_id"))
    @Column(name = "namespace_id", nullable = false)
    private Set<Long> visibleNamespaceIds = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "catalog_resource_scenario", joinColumns = @JoinColumn(name = "resource_id"))
    @Column(name = "scenario", nullable = false, length = 96)
    private Set<String> scenarios = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "catalog_resource_tag", joinColumns = @JoinColumn(name = "resource_id"))
    @Column(name = "tag", nullable = false, length = 64)
    private Set<String> tags = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "catalog_resource_agent_example_prompt", joinColumns = @JoinColumn(name = "resource_id"))
    @Column(name = "prompt", nullable = false, length = 1000)
    private Set<String> agentExamplePrompts = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "catalog_resource_relation", joinColumns = @JoinColumn(name = "source_resource_id"))
    @Column(name = "target_resource_id", nullable = false)
    private Set<Long> relatedResourceIds = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "catalog_resource_skill_relation", joinColumns = @JoinColumn(name = "resource_id"))
    @Column(name = "skill_id", nullable = false)
    private Set<Long> relatedSkillIds = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected CatalogResource() {
    }

    public CatalogResource(CatalogResourceDraft draft, String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw CatalogDomainException.badRequest("error.catalog.owner.required");
        }
        this.ownerId = ownerId;
        apply(draft, true);
    }

    public void update(CatalogResourceDraft draft) {
        apply(draft, false);
    }

    public void publish(Instant now) {
        if (documentation == null || documentation.isBlank()) {
            throw CatalogDomainException.badRequest("error.catalog.documentation.required");
        }
        if (kind == CatalogResourceKind.AGENT) {
            requireAgentPublishFields();
        }
        this.status = CatalogResourceStatus.PUBLISHED;
        this.publishedAt = now;
    }

    public void takeOffline() {
        if (status == CatalogResourceStatus.DRAFT) {
            throw CatalogDomainException.badRequest("error.catalog.offline.draft");
        }
        this.status = CatalogResourceStatus.OFFLINE;
    }

    public void transferOwnership(String newOwnerId) {
        if (newOwnerId == null || newOwnerId.isBlank()) {
            throw CatalogDomainException.badRequest("error.catalog.owner.required");
        }
        this.ownerId = newOwnerId.trim();
    }

    public void attachArtifact(String storageKey, String filename, String contentType, long size) {
        if (storageKey == null || storageKey.isBlank() || filename == null || filename.isBlank() || size < 0) {
            throw CatalogDomainException.badRequest("error.catalog.artifact.invalid");
        }
        this.artifactStorageKey = storageKey;
        this.artifactFilename = filename;
        this.artifactContentType = contentType;
        this.artifactSize = size;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceKey = normalizeNullable(sourceKey);
    }

    private void apply(CatalogResourceDraft draft, boolean allowSlugChange) {
        if (draft == null) {
            throw CatalogDomainException.badRequest("error.catalog.request.required");
        }
        String normalizedSlug = normalizeRequired(draft.slug(), "error.catalog.slug.required");
        if (!SLUG_PATTERN.matcher(normalizedSlug).matches() || normalizedSlug.length() > 96) {
            throw CatalogDomainException.badRequest("error.catalog.slug.invalid", normalizedSlug);
        }
        if (allowSlugChange) {
            this.slug = normalizedSlug;
        } else if (!slug.equals(normalizedSlug)) {
            throw CatalogDomainException.badRequest("error.catalog.slug.immutable");
        }
        this.name = requireLength(draft.name(), 160, "error.catalog.name.required", "error.catalog.name.tooLong");
        this.summary = requireLength(draft.summary(), 1200, "error.catalog.summary.required", "error.catalog.summary.tooLong");
        this.kind = require(draft.kind(), "error.catalog.kind.required");
        this.icon = trimToLength(draft.icon(), 256, "error.catalog.icon.tooLong");
        this.accessUrl = trimToLength(draft.accessUrl(), 1024, "error.catalog.accessUrl.tooLong");
        this.documentation = normalizeNullable(draft.documentation());
        this.version = trimToLength(draft.version(), 64, "error.catalog.version.tooLong");
        this.agentUsageBoundary = normalizeNullable(draft.agentUsageBoundary());
        this.agentInputGuide = normalizeNullable(draft.agentInputGuide());
        this.agentOutputGuide = normalizeNullable(draft.agentOutputGuide());
        this.agentSupportContact = trimToLength(draft.agentSupportContact(), 256, "error.catalog.agent.supportContact.tooLong");
        this.agentExamplePrompts = normalizedStrings(draft.agentExamplePrompts(), 1000, "error.catalog.agent.examplePrompt.tooLong");
        this.primaryNamespaceId = draft.primaryNamespaceId();
        this.maintenanceStatus = draft.maintenanceStatus() != null
                ? draft.maintenanceStatus() : CatalogMaintenanceStatus.ACTIVE;
        this.visibilityScope = draft.visibilityScope() != null
                ? draft.visibilityScope() : CatalogVisibilityScope.COMPANY;
        this.visibleNamespaceIds = normalizedLongs(draft.visibleNamespaceIds());
        if (visibilityScope == CatalogVisibilityScope.DEPARTMENTS && visibleNamespaceIds.isEmpty()) {
            throw CatalogDomainException.badRequest("error.catalog.visibility.departmentsRequired");
        }
        this.scenarios = normalizedStrings(draft.scenarios(), 96, "error.catalog.scenario.tooLong");
        this.tags = normalizedStrings(draft.tags(), 64, "error.catalog.tag.tooLong");
        this.relatedResourceIds = normalizedLongs(draft.relatedResourceIds());
        this.relatedSkillIds = normalizedLongs(draft.relatedSkillIds());
    }

    private void requireAgentPublishFields() {
        if (accessUrl == null) {
            throw CatalogDomainException.badRequest("error.catalog.agent.accessUrl.required");
        }
        if (scenarios.isEmpty()) {
            throw CatalogDomainException.badRequest("error.catalog.agent.scenario.required");
        }
    }

    private static String normalizeRequired(String value, String code) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw CatalogDomainException.badRequest(code);
        }
        return normalized;
    }

    private static String requireLength(String value, int max, String requiredCode, String lengthCode) {
        String normalized = normalizeRequired(value, requiredCode);
        if (normalized.length() > max) {
            throw CatalogDomainException.badRequest(lengthCode, max);
        }
        return normalized;
    }

    private static String trimToLength(String value, int max, String code) {
        String normalized = normalizeNullable(value);
        if (normalized != null && normalized.length() > max) {
            throw CatalogDomainException.badRequest(code, max);
        }
        return normalized;
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static <T> T require(T value, String code) {
        if (value == null) {
            throw CatalogDomainException.badRequest(code);
        }
        return value;
    }

    private static Set<Long> normalizedLongs(Set<Long> values) {
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        if (values != null) {
            values.stream().filter(value -> value != null && value > 0).forEach(normalized::add);
        }
        return normalized;
    }

    private static Set<String> normalizedStrings(Set<String> values, int max, String code) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values == null) {
            return normalized;
        }
        for (String value : values) {
            String item = normalizeNullable(value);
            if (item == null) {
                continue;
            }
            if (item.length() > max) {
                throw CatalogDomainException.badRequest(code, max);
            }
            normalized.add(item);
        }
        return normalized;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now(Clock.systemUTC());
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getSummary() { return summary; }
    public CatalogResourceKind getKind() { return kind; }
    public String getIcon() { return icon; }
    public String getAccessUrl() { return accessUrl; }
    public String getDocumentation() { return documentation; }
    public String getVersion() { return version; }
    public String getAgentUsageBoundary() { return agentUsageBoundary; }
    public String getAgentInputGuide() { return agentInputGuide; }
    public String getAgentOutputGuide() { return agentOutputGuide; }
    public String getAgentSupportContact() { return agentSupportContact; }
    public Long getPrimaryNamespaceId() { return primaryNamespaceId; }
    public String getOwnerId() { return ownerId; }
    public CatalogResourceStatus getStatus() { return status; }
    public CatalogMaintenanceStatus getMaintenanceStatus() { return maintenanceStatus; }
    public CatalogVisibilityScope getVisibilityScope() { return visibilityScope; }
    public String getArtifactStorageKey() { return artifactStorageKey; }
    public String getArtifactFilename() { return artifactFilename; }
    public String getArtifactContentType() { return artifactContentType; }
    public Long getArtifactSize() { return artifactSize; }
    public String getSourceKey() { return sourceKey; }
    public Set<Long> getVisibleNamespaceIds() { return Set.copyOf(visibleNamespaceIds); }
    public Set<String> getScenarios() { return Set.copyOf(scenarios); }
    public Set<String> getTags() { return Set.copyOf(tags); }
    public Set<String> getAgentExamplePrompts() { return Set.copyOf(agentExamplePrompts); }
    public Set<Long> getRelatedResourceIds() { return Set.copyOf(relatedResourceIds); }
    public Set<Long> getRelatedSkillIds() { return Set.copyOf(relatedSkillIds); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public boolean hasArtifact() { return artifactStorageKey != null; }
}
