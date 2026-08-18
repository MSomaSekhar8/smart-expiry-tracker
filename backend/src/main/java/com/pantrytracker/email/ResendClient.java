package com.pantrytracker.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.pantrytracker.notification.ExpiryDigestTemplate;

/**
 * Sends email through the Resend HTTP API. When no API key is configured
 * (local dev) the digest is only logged — the job stays idempotent and safe.
 */
@Component
public class ResendClient {

    private static final Logger log = LoggerFactory.getLogger(ResendClient.class);

    private final RestClient restClient;
    private final String from;
    private final boolean enabled;

    public ResendClient(@Value("${app.resend.api-key}") String apiKey,
                        @Value("${app.resend.from}") String from) {
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.from = from;
        this.restClient = enabled
                ? RestClient.builder()
                        .baseUrl("https://api.resend.com")
                        .defaultHeader("Authorization", "Bearer " + apiKey)
                        .build()
                : null;
    }

    public void sendDigest(java.util.List<ExpiryDigestTemplate.DigestLine> expiringSoon,
                           java.util.List<ExpiryDigestTemplate.DigestLine> expired) {
        if (expiringSoon.isEmpty() && expired.isEmpty()) {
            return;
        }
        String html = ExpiryDigestTemplate.render(expiringSoon, expired);
        if (!enabled) {
            log.info("[digest dry-run] {} expiring soon, {} expired — RESEND_API_KEY not set",
                    expiringSoon.size(), expired.size());
            return;
        }
        try {
            restClient.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of(
                            "from", from,
                            "to", java.util.List.of("user@pantrytracker.app"),
                            "subject", "Pantry digest: " + expiringSoon.size()
                                    + " expiring, " + expired.size() + " expired",
                            "html", html))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Digest email send failed: {}", ex.getMessage());
        }
    }
}