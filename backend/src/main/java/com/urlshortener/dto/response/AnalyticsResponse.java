package com.urlshortener.dto.response;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {

    private String shortCode;
    private long totalClicks;
    private long uniqueVisitors;
    private Map<String, Long> browsers;
    private Map<String, Long> operatingSystems;
    private Map<String, Long> deviceTypes;
    private Map<String, Long> countries;
    private Map<String, Long> referrers;
    private List<DailyClickCount> dailyClicks;
}
