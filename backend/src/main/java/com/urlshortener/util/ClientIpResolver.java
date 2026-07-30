package com.urlshortener.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;

/** Resolves the originating client IP, accounting for the nginx reverse proxy in front of the app. */
@UtilityClass
public class ClientIpResolver {

    private static final String[] HEADERS_TO_CHECK = {
        "X-Forwarded-For",
        "X-Real-IP"
    };

    public String resolve(HttpServletRequest request) {
        for (String header : HEADERS_TO_CHECK) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                // X-Forwarded-For may contain a comma-separated chain; the first entry is the original client.
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
