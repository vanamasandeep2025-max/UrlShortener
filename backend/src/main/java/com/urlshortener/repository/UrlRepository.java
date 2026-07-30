package com.urlshortener.repository;

import com.urlshortener.entity.Url;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UrlRepository extends JpaRepository<Url, UUID>, JpaSpecificationExecutor<Url> {

    Optional<Url> findByShortCodeAndDeletedAtIsNull(String shortCode);

    boolean existsByShortCodeAndDeletedAtIsNull(String shortCode);

    /**
     * clearAutomatically=true: bulk JPQL updates write straight to the DB and bypass the
     * persistence context, so without clearing it, a subsequent findById() in the same
     * transaction/session would silently return the stale pre-update in-memory entity.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Url u SET u.clickCount = u.clickCount + 1 WHERE u.id = :id")
    int incrementClickCount(@Param("id") UUID id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Url u SET u.deletedAt = :now WHERE u.expiresAt < :now AND u.deletedAt IS NULL")
    int softDeleteExpiredUrls(@Param("now") Instant now);
}
