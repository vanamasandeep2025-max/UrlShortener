package com.urlshortener.util.useragent;

import com.urlshortener.entity.DeviceType;
import java.util.Locale;
import org.springframework.stereotype.Service;
import ua_parser.Client;
import ua_parser.Parser;

/**
 * Wraps ua-parser (the browser-scope requirement for Scenario 2: browser/OS/device
 * analytics). ua-parser's own "device" field identifies specific device models rather
 * than broad categories, so DESKTOP/MOBILE/TABLET/BOT classification is a heuristic on
 * top of it - documented as a known simplification in
 * AI_ENGINEERING/scenarios/02_brownfield_browser_detection.md.
 */
@Service
public class UserAgentParsingService {

    private final Parser parser = new Parser();

    public ParsedUserAgent parse(String userAgentHeader) {
        if (userAgentHeader == null || userAgentHeader.isBlank()) {
            return new ParsedUserAgent(null, null, null, null, DeviceType.OTHER);
        }
        Client client = parser.parse(userAgentHeader);
        String browser = client.userAgent != null ? client.userAgent.family : null;
        String browserVersion = client.userAgent != null ? joinVersion(client.userAgent.major, client.userAgent.minor) : null;
        String os = client.os != null ? client.os.family : null;
        String osVersion = client.os != null ? joinVersion(client.os.major, client.os.minor) : null;
        DeviceType deviceType = classify(userAgentHeader);
        return new ParsedUserAgent(browser, browserVersion, os, osVersion, deviceType);
    }

    private String joinVersion(String major, String minor) {
        if (major == null) {
            return null;
        }
        return minor == null ? major : major + "." + minor;
    }

    private DeviceType classify(String userAgentHeader) {
        String ua = userAgentHeader.toLowerCase(Locale.ROOT);
        if (ua.contains("bot") || ua.contains("spider") || ua.contains("crawl") || ua.contains("slurp")) {
            return DeviceType.BOT;
        }
        if (ua.contains("ipad") || ua.contains("tablet") || (ua.contains("android") && !ua.contains("mobile"))) {
            return DeviceType.TABLET;
        }
        if (ua.contains("mobi") || ua.contains("iphone") || ua.contains("android")) {
            return DeviceType.MOBILE;
        }
        return DeviceType.DESKTOP;
    }
}
