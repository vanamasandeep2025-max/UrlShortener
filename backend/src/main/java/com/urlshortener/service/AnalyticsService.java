package com.urlshortener.service;

import com.urlshortener.dto.response.AnalyticsResponse;
import java.util.UUID;

public interface AnalyticsService {

    AnalyticsResponse buildAnalytics(UUID urlId, String shortCode);
}
