package com.iflytek.skillhub.domain.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Clock;
import java.time.Instant;

/** A read/use access grant from a skill to an additional department namespace. */
@Entity
@Table(name = "skill_visible_namespace", uniqueConstraints = @UniqueConstraint(
        name = "uk_skill_visible_namespace", columnNames = {"skill_id", "namespace_id"}))
public class SkillVisibleNamespace {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SkillVisibleNamespace() {
    }

    public SkillVisibleNamespace(Long skillId, Long namespaceId) {
        this.skillId = skillId;
        this.namespaceId = namespaceId;
    }

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        createdAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() {
        return id;
    }

    public Long getSkillId() {
        return skillId;
    }

    public Long getNamespaceId() {
        return namespaceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
