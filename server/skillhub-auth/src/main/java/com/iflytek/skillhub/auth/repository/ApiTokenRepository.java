package com.iflytek.skillhub.auth.repository;

import com.iflytek.skillhub.auth.entity.ApiToken;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for personal API tokens and token listings scoped to one user.
 */
@Repository
public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {
    Optional<ApiToken> findByTokenHash(String tokenHash);
    List<ApiToken> findByUserId(String userId);
    List<ApiToken> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(String userId);
    Page<ApiToken> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(String userId, Pageable pageable);
    boolean existsByUserIdAndRevokedAtIsNullAndNameIgnoreCase(String userId, String name);
    Optional<ApiToken> findByUserIdAndNameIgnoreCaseAndRevokedAtIsNull(String userId, String name);

    @Modifying
    @Query("update ApiToken token set token.lastUsedAt = :lastUsedAt where token.id = :id")
    void updateLastUsedAt(@Param("id") Long id, @Param("lastUsedAt") Instant lastUsedAt);
}
