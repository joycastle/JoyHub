package com.iflytek.skillhub.infra.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Durable, aggregate-neutral projection used by unified resource discovery. */
@Entity
@DynamicUpdate
@Table(name = "resource_search_document")
public class ResourceSearchDocumentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "resource_type", nullable = false, length = 16) private String resourceType;
    @Column(name = "resource_id", nullable = false) private Long resourceId;
    @Column(name = "namespace_id") private Long namespaceId;
    @Column(name = "owner_id", nullable = false, length = 128) private String ownerId;
    @Column(nullable = false, length = 512) private String title;
    @Column(nullable = false, length = 160) private String slug;
    @Column(columnDefinition = "TEXT") private String summary;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "capabilities_json", columnDefinition = "JSONB") private String capabilitiesJson = "[]";
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "scenarios_json", columnDefinition = "JSONB") private String scenariosJson = "[]";
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "inputs_json", columnDefinition = "JSONB") private String inputsJson = "[]";
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "outputs_json", columnDefinition = "JSONB") private String outputsJson = "[]";
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "search_terms_json", columnDefinition = "JSONB") private String searchTermsJson = "[]";
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "evidence_json", columnDefinition = "JSONB") private String evidenceJson = "[]";
    @Column(name = "access_mode", nullable = false, length = 16) private String accessMode;
    @Column(nullable = false, length = 32) private String visibility;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "company_relevance", nullable = false, length = 16) private String companyRelevance = "GENERAL";
    @Enumerated(EnumType.STRING)
    @Column(name = "category_code", nullable = false, length = 32) private ResourceCategoryCode categoryCode = ResourceCategoryCode.OTHER;
    @Enumerated(EnumType.STRING)
    @Column(name = "category_source", nullable = false, length = 16) private ResourceCategorySource categorySource = ResourceCategorySource.AI;
    @Column(name = "search_enabled", nullable = false) private boolean searchEnabled = true;
    @Column(name = "profile_text", nullable = false, columnDefinition = "TEXT") private String profileText = "";
    @Column(name = "raw_documentation", nullable = false, columnDefinition = "TEXT") private String rawDocumentation = "";
    @Column(name = "semantic_vector", columnDefinition = "TEXT") private String semanticVector;
    @Column(name = "source_hash", nullable = false, length = 64) private String sourceHash;
    @Column(name = "generation_status", nullable = false, length = 16) private String generationStatus = "BASIC";
    @Column(name = "generator_model", length = 128) private String generatorModel;
    @Column(name = "prompt_version", length = 32) private String promptVersion;
    @Column(name = "generated_at") private Instant generatedAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @Column(name = "generation_attempts", nullable = false) private int generationAttempts;
    @Column(name = "generation_error_code", length = 64) private String generationErrorCode;
    @Column(name = "generation_next_retry_at") private Instant generationNextRetryAt;
    @Column(name = "generation_lease_token", length = 36) private String generationLeaseToken;
    @Column(name = "generation_lease_until") private Instant generationLeaseUntil;

    protected ResourceSearchDocumentEntity() { }

    public static ResourceSearchDocumentEntity basic(String resourceType, Long resourceId, Long namespaceId,
                                                     String ownerId, String title, String slug, String summary,
                                                     String scenariosJson, String accessMode, String visibility,
                                                     String status, String rawDocumentation, String sourceHash) {
        ResourceSearchDocumentEntity entity = new ResourceSearchDocumentEntity();
        entity.resourceType = resourceType;
        entity.resourceId = resourceId;
        entity.namespaceId = namespaceId;
        entity.ownerId = ownerId;
        entity.title = title;
        entity.slug = slug;
        entity.summary = summary;
        entity.scenariosJson = scenariosJson;
        entity.accessMode = accessMode;
        entity.visibility = visibility;
        entity.status = status;
        entity.profileText = String.join("\n", title, summary == null ? "" : summary);
        entity.rawDocumentation = rawDocumentation;
        entity.sourceHash = sourceHash;
        entity.generationStatus = "PENDING";
        return entity;
    }

    public void refreshBasic(Long namespaceId, String ownerId, String title, String slug, String summary,
                             String scenariosJson, String accessMode, String visibility, String status,
                             String rawDocumentation, String sourceHash, boolean enabled) {
        boolean changed = !sourceHash.equals(this.sourceHash);
        this.namespaceId = namespaceId;
        this.ownerId = ownerId;
        this.title = title;
        this.slug = slug;
        this.summary = summary;
        this.scenariosJson = scenariosJson;
        this.accessMode = accessMode;
        this.visibility = visibility;
        this.status = status;
        this.rawDocumentation = rawDocumentation;
        this.sourceHash = sourceHash;
        this.searchEnabled = enabled;
        if (changed) {
            this.profileText = String.join("\n", title, summary == null ? "" : summary);
            requestRegeneration();
        }
    }

    public void applyGeneratedProfile(String capabilitiesJson, String scenariosJson, String inputsJson,
                                      String outputsJson, String searchTermsJson, String evidenceJson,
                                      String companyRelevance, String profileText, String generatorModel,
                                      String promptVersion, String semanticVector,
                                      ResourceCategoryCode generatedCategory) {
        this.capabilitiesJson = capabilitiesJson;
        this.scenariosJson = scenariosJson;
        this.inputsJson = inputsJson;
        this.outputsJson = outputsJson;
        this.searchTermsJson = searchTermsJson;
        this.evidenceJson = evidenceJson;
        this.companyRelevance = companyRelevance;
        applyAiCategory(generatedCategory);
        this.profileText = profileText;
        this.generatorModel = generatorModel;
        this.promptVersion = promptVersion;
        this.semanticVector = semanticVector;
        this.generationStatus = "READY";
        this.generatedAt = Instant.now();
        this.generationErrorCode = null;
        this.generationNextRetryAt = null;
        this.generationLeaseToken = null;
        this.generationLeaseUntil = null;
    }

    public void markGenerationFailed() {
        this.generationStatus = "FAILED";
    }

    public void requestRegeneration() {
        this.generationStatus = "PENDING";
        this.generationAttempts = 0;
        this.generationErrorCode = null;
        this.generationNextRetryAt = null;
        this.generationLeaseToken = null;
        this.generationLeaseUntil = null;
        this.semanticVector = null;
        this.generatedAt = null;
    }

    public boolean canClaimGeneration(Instant now) {
        if (!searchEnabled || generationLeaseUntil != null && generationLeaseUntil.isAfter(now)) return false;
        return "PENDING".equals(generationStatus) || "FAILED".equals(generationStatus)
                && generationNextRetryAt != null && !generationNextRetryAt.isAfter(now);
    }

    public void claimGeneration(String token, Instant leaseUntil) {
        generationAttempts++;
        generationLeaseToken = token;
        generationLeaseUntil = leaseUntil;
    }

    public boolean ownsGeneration(String token, String expectedHash) {
        return searchEnabled && token.equals(generationLeaseToken) && expectedHash.equals(sourceHash);
    }

    public void failGeneration(String errorCode, Instant nextRetryAt) {
        generationStatus = "FAILED";
        generationErrorCode = errorCode;
        generationNextRetryAt = nextRetryAt;
        generationLeaseToken = null;
        generationLeaseUntil = null;
    }

    /** Records a human-selected category; subsequent AI generations must preserve it. */
    public void setAuthorCategory(ResourceCategoryCode category) {
        this.categoryCode = category == null ? ResourceCategoryCode.OTHER : category;
        this.categorySource = ResourceCategorySource.AUTHOR;
    }

    /** Returns ownership of category selection to AI and queues a fresh profile generation. */
    public void useAiCategory() {
        this.categoryCode = ResourceCategoryCode.OTHER;
        this.categorySource = ResourceCategorySource.AI;
        requestRegeneration();
    }

    /** Applies an AI category only while the document is not human-owned. */
    public void applyAiCategory(ResourceCategoryCode generatedCategory) {
        if (categorySource != ResourceCategorySource.AUTHOR) {
            this.categoryCode = generatedCategory == null ? ResourceCategoryCode.OTHER : generatedCategory;
            this.categorySource = ResourceCategorySource.AI;
        }
    }

    public Long getId() { return id; }
    public int getGenerationAttempts() { return generationAttempts; }
    public String getGenerationErrorCode() { return generationErrorCode; }
    public Instant getGenerationNextRetryAt() { return generationNextRetryAt; }
    public String getGenerationLeaseToken() { return generationLeaseToken; }
    public Instant getGenerationLeaseUntil() { return generationLeaseUntil; }
    public Long getResourceId() { return resourceId; }
    public String getResourceType() { return resourceType; }
    public String getTitle() { return title; }
    public String getSlug() { return slug; }
    public String getSummary() { return summary; }
    public String getCapabilitiesJson() { return capabilitiesJson; }
    public String getScenariosJson() { return scenariosJson; }
    public String getInputsJson() { return inputsJson; }
    public String getOutputsJson() { return outputsJson; }
    public String getSearchTermsJson() { return searchTermsJson; }
    public String getProfileText() { return profileText; }
    public String getRawDocumentation() { return rawDocumentation; }
    public String getAccessMode() { return accessMode; }
    public String getSemanticVector() { return semanticVector; }
    public boolean isSearchEnabled() { return searchEnabled; }
    public String getGenerationStatus() { return generationStatus; }
    public String getCompanyRelevance() { return companyRelevance; }
    public ResourceCategoryCode getCategoryCode() { return categoryCode; }
    public ResourceCategorySource getCategorySource() { return categorySource; }
    public String getEvidenceJson() { return evidenceJson; }
    public String getSourceHash() { return sourceHash; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    @PrePersist @PreUpdate protected void updateTimestamp() { updatedAt = Instant.now(); }
}
