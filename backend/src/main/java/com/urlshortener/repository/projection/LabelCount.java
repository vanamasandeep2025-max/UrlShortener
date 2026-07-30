package com.urlshortener.repository.projection;

/** Generic (label, count) projection used for browser/OS/device/country/referrer breakdowns. */
public interface LabelCount {

    String getLabel();

    Long getCount();
}
