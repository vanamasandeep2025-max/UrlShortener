package com.urlshortener.repository;

import com.urlshortener.entity.Url;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Composable filter predicates for the list-URLs endpoint (pagination/sort/filter/search). */
public final class UrlSpecifications {

    private UrlSpecifications() {
    }

    public static Specification<Url> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Url> ownedBy(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Url> searchOriginalUrlOrCode(String search) {
        String like = "%" + search.toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
            cb.like(cb.lower(root.get("originalUrl")), like),
            cb.like(cb.lower(root.get("shortCode")), like));
    }

    public static Specification<Url> statusIs(String status) {
        Instant now = Instant.now();
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> (root, query, cb) -> cb.or(
                cb.isNull(root.get("expiresAt")),
                cb.greaterThan(root.get("expiresAt"), now));
            case "EXPIRED" -> (root, query, cb) -> cb.and(
                cb.isNotNull(root.get("expiresAt")),
                cb.lessThanOrEqualTo(root.get("expiresAt"), now));
            default -> null;
        };
    }
}
