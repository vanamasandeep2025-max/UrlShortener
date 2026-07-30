package com.urlshortener.service.impl;

import com.urlshortener.dto.response.AnalyticsResponse;
import com.urlshortener.dto.response.DailyClickCount;
import com.urlshortener.repository.UrlClickRepository;
import com.urlshortener.repository.projection.LabelCount;
import com.urlshortener.service.AnalyticsService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final UrlClickRepository urlClickRepository;

    @Override
    // Don't cache an all-zero snapshot: a freshly created link has no clicks yet, and that
    // "nothing happened" state is exactly the one most likely to change soon after - caching
    // it would hide the first real click from readers for the full TTL window.
    @Cacheable(cacheNames = "analytics", key = "#shortCode", unless = "#result.totalClicks == 0")
    @Transactional(readOnly = true)
    public AnalyticsResponse buildAnalytics(UUID urlId, String shortCode) {
        // Same @Cacheable short-circuit note as UrlServiceImpl#resolveForRedirect: this only
        // logs on a cache miss (a real aggregation query), not on every cached read.
        log.info("Analytics computed (cache miss): shortCode={} urlId={}", shortCode, urlId);
        long totalClicks = urlClickRepository.countByUrlId(urlId);
        long uniqueVisitors = urlClickRepository.countDistinctVisitors(urlId);

        return AnalyticsResponse.builder()
            .shortCode(shortCode)
            .totalClicks(totalClicks)
            .uniqueVisitors(uniqueVisitors)
            .browsers(toMap(urlClickRepository.countByBrowser(urlId)))
            .operatingSystems(toMap(urlClickRepository.countByOs(urlId)))
            .deviceTypes(toMap(urlClickRepository.countByDeviceType(urlId)))
            .countries(toMap(urlClickRepository.countByCountry(urlId)))
            .referrers(toMap(urlClickRepository.countByReferrer(urlId)))
            .dailyClicks(urlClickRepository.countByDay(urlId).stream()
                .map(d -> DailyClickCount.builder().date(d.getDay()).count(d.getCount()).build())
                .collect(Collectors.toList()))
            .build();
    }

    private Map<String, Long> toMap(List<LabelCount> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        rows.forEach(row -> map.put(row.getLabel(), row.getCount()));
        return map;
    }
}
