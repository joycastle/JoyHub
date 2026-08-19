package com.iflytek.skillhub.domain.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "skill_documentation_translation")
public class SkillDocumentationTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(nullable = false, length = 512)
    private String path;

    @Column(nullable = false, length = 16)
    private String language;

    @Column(name = "source_sha256", nullable = false, length = 64)
    private String sourceSha256;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String markdown;

    @Column(length = 64)
    private String model;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SkillDocumentationTranslation() {
    }

    public SkillDocumentationTranslation(Long skillId, Long versionId, String path, String language,
                                         String sourceSha256, String markdown, String model) {
        this.skillId = skillId;
        this.versionId = versionId;
        this.path = path;
        this.language = language;
        this.sourceSha256 = sourceSha256;
        this.markdown = markdown;
        this.model = model;
    }

    public void replace(String sourceSha256, String markdown, String model) {
        this.sourceSha256 = sourceSha256;
        this.markdown = markdown;
        this.model = model;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now(Clock.systemUTC());
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() {
        return id;
    }

    public Long getSkillId() {
        return skillId;
    }

    public Long getVersionId() {
        return versionId;
    }

    public String getPath() {
        return path;
    }

    public String getLanguage() {
        return language;
    }

    public String getSourceSha256() {
        return sourceSha256;
    }

    public String getMarkdown() {
        return markdown;
    }

    public String getModel() {
        return model;
    }
}
