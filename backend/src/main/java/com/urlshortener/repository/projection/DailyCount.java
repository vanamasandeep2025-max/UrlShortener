package com.urlshortener.repository.projection;

import java.time.LocalDate;

public interface DailyCount {

    LocalDate getDay();

    Long getCount();
}
