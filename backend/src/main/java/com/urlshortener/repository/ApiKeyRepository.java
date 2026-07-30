package com.urlshortener.repository;

import com.urlshortener.entity.ApiKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /**
     * Eagerly fetches the owning user in the same query. Used exclusively by the
     * authentication filter, which runs outside any request-scoped transaction, so the
     * lazy `user` association must already be initialized before the Hibernate session closes.
     */
    @Query("SELECT k FROM ApiKey k JOIN FETCH k.user WHERE k.keyHash = :keyHash")
    Optional<ApiKey> findByKeyHashFetchUser(@Param("keyHash") String keyHash);

    List<ApiKey> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(UUID userId);
}
