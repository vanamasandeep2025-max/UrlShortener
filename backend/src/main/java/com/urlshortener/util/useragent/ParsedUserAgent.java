package com.urlshortener.util.useragent;

import com.urlshortener.entity.DeviceType;

public record ParsedUserAgent(String browser, String browserVersion, String os, String osVersion, DeviceType deviceType) {
}
