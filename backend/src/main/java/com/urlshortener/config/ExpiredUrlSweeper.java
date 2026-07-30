package com.urlshortener.config;

import com.urlshortener.repository.UrlRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expiry is already enforced lazily at redirect time (RedirectController checks
 * UrlRedirectTarget.isExpired()); this sweep just soft-deletes expired rows in the
 * background so list views and the analytics table don't accumulate long-expired,
 * never-visited links indefinitely.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredUrlSweeper {

    private final UrlRepository urlRepository;

    @Scheduled(fixedRateString = "${app.expiry-sweep.interval-ms:900000}")
    @Transactional
    public void sweepExpiredUrls() {
        int updated = urlRepository.softDeleteExpiredUrls(Instant.now());
        if (updated > 0) {
            log.info("Expired-URL sweep soft-deleted {} link(s)", updated);
        }
    }
}
