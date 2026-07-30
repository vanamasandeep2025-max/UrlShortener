package com.urlshortener.repository;

import com.urlshortener.entity.UrlClick;
import com.urlshortener.repository.projection.DailyCount;
import com.urlshortener.repository.projection.LabelCount;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UrlClickRepository extends JpaRepository<UrlClick, UUID> {

    boolean existsByEventId(UUID eventId);

    long countByUrlId(UUID urlId);

    @Query("SELECT COUNT(DISTINCT c.ipHash) FROM UrlClick c WHERE c.url.id = :urlId")
    long countDistinctVisitors(@Param("urlId") UUID urlId);

    @Query("SELECT COALESCE(c.browser, 'Unknown') as label, COUNT(c) as count "
        + "FROM UrlClick c WHERE c.url.id = :urlId GROUP BY c.browser ORDER BY count DESC")
    List<LabelCount> countByBrowser(@Param("urlId") UUID urlId);

    @Query("SELECT COALESCE(c.os, 'Unknown') as label, COUNT(c) as count "
        + "FROM UrlClick c WHERE c.url.id = :urlId GROUP BY c.os ORDER BY count DESC")
    List<LabelCount> countByOs(@Param("urlId") UUID urlId);

    @Query("SELECT COALESCE(CAST(c.deviceType as string), 'UNKNOWN') as label, COUNT(c) as count "
        + "FROM UrlClick c WHERE c.url.id = :urlId GROUP BY c.deviceType ORDER BY count DESC")
    List<LabelCount> countByDeviceType(@Param("urlId") UUID urlId);

    @Query("SELECT COALESCE(c.country, 'Unknown') as label, COUNT(c) as count "
        + "FROM UrlClick c WHERE c.url.id = :urlId GROUP BY c.country ORDER BY count DESC")
    List<LabelCount> countByCountry(@Param("urlId") UUID urlId);

    @Query("SELECT COALESCE(c.referrer, 'Direct') as label, COUNT(c) as count "
        + "FROM UrlClick c WHERE c.url.id = :urlId GROUP BY c.referrer ORDER BY count DESC")
    List<LabelCount> countByReferrer(@Param("urlId") UUID urlId);

    @Query("SELECT CAST(c.clickedAt as date) as day, COUNT(c) as count "
        + "FROM UrlClick c WHERE c.url.id = :urlId GROUP BY CAST(c.clickedAt as date) ORDER BY day")
    List<DailyCount> countByDay(@Param("urlId") UUID urlId);
}
