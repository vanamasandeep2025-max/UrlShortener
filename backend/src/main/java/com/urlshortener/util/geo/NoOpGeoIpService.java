package com.urlshortener.util.geo;

import org.springframework.stereotype.Service;

/**
 * Default GeoIpService: no geo database is bundled with this project (MaxMind GeoIP2's
 * database requires a license and periodic updates, out of scope for this exercise), so
 * country is always unresolved. Swap in a real implementation by providing another
 * GeoIpService bean and excluding this one (e.g. via a profile or @ConditionalOnMissingBean).
 */
@Service
public class NoOpGeoIpService implements GeoIpService {

    @Override
    public String lookupCountry(String ipAddress) {
        return null;
    }
}
