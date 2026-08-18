package com.pantrytracker.notification;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class DigestController {

    private final ExpiryDigestService digestService;

    public DigestController(ExpiryDigestService digestService) {
        this.digestService = digestService;
    }

    @PostMapping("/digest/test")
    @PreAuthorize("hasRole('ADMIN')")
    public DigestResult triggerDigest() {
        ExpiryDigestService.DigestReport report = digestService.run();
        return new DigestResult(report.expiringSoonCount(), report.expiredCount());
    }

    public record DigestResult(int expiringSoonCount, int expiredCount) {}
}