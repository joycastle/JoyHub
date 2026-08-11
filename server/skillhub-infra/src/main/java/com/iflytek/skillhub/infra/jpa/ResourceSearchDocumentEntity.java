package com.iflytek.skillhub.infra.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Durable, aggregate-neutral projection used by unified resource discovery. */
@Entity
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
            this.generationStatus = "PENDING";
            this.semanticVector = null;
            this.generatedAt = null;
        }
    }

    public void applyGeneratedProfile(String capabilitiesJson, String scenariosJson, String inputsJson,
                                      String outputsJson, String searchTermsJson, String evidenceJson,
                                      String companyRelevance, String profileText, String generatorModel,
                                      String promptVersion, String semanticVector) {
        this.capabilitiesJson = capabilitiesJson;
        this.scenariosJson = scenariosJson;
        this.inputsJson = inputsJson;
        this.outputsJson = outputsJson;
        this.searchTermsJson = searchTermsJson;
        this.evidenceJson = evidenceJson;
        this.companyRelevance = companyRelevance;
        this.profileText = profileText;
        this.generatorModel = generatorModel;
        this.promptVersion = promptVersion;
        this.semanticVector = semanticVector;
        this.generationStatus = "READY";
        this.generatedAt = Instant.now();
    }

    public void markGenerationFailed() {
        this.generationStatus = "FAILED";
    }

    public void requestRegeneration() {
        this.generationStatus = "PENDING";
        this.semanticVector = null;
        this.generatedAt = null;
    }

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
    public String getEvidenceJson() { return evidenceJson; }
    public String getSourceHash() { return sourceHash; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    @PrePersist @PreUpdate protected void updateTimestamp() { updatedAt = Instant.now(); }
}
