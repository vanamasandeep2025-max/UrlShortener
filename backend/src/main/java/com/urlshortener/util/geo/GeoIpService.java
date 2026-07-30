package com.urlshortener.util.geo;

/**
 * Resolves a client IP to an ISO-3166-1 alpha-2 country code. Pluggable so the default
 * no-op implementation can be swapped for a real provider (e.g. MaxMind GeoIP2) without
 * touching the analytics consumer - see AI_ENGINEERING/scenarios for the rationale on
 * why no geo database is bundled with this project.
 */
public interface GeoIpService {

    /** Returns null when the country cannot be determined. */
    String lookupCountry(String ipAddress);
}
