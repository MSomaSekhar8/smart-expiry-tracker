package com.pantrytracker.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the digest at 07:00 daily (configurable via app.digest.cron).
 * A second @Scheduled on the manual endpoint is intentionally NOT used —
 * the admin "test" endpoint calls the same service on demand.
 */
@Component
public class ExpiryDigestJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiryDigestJob.class);

    private final ExpiryDigestService digestService;

    public ExpiryDigestJob(ExpiryDigestService digestService) {
        this.digestService = digestService;
    }

    @Scheduled(cron = "${app.digest.cron}")
    public void runDailyDigest() {
        ExpiryDigestService.DigestReport report = digestService.run();
        log.info("Daily digest complete: {} expiring soon, {} expired",
                report.expiringSoonCount(), report.expiredCount());
    }
}