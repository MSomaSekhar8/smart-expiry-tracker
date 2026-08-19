package com.pantrytracker.email;

import com.pantrytracker.notification.ExpiryDigestTemplate;
import com.pantrytracker.user.User;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Sends email through the Resend HTTP API. When no API key is configured
 * (local dev) the digest is only logged as counts — no item names, no email
 * addresses, no tokens — and the job stays idempotent and safe.
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

    /**
     * Sends ONE digest to the given user containing ONLY that user's items.
     * The recipient is always the user's own stored email — never a hardcoded
     * address and never another user's mailbox.
     */
    public void sendDigest(User user, List<ExpiryDigestTemplate.DigestLine> expiringSoon,
                           List<ExpiryDigestTemplate.DigestLine> expired) {
        if (expiringSoon.isEmpty() && expired.isEmpty()) {
            return;
        }
        String recipient = user == null ? null : user.getEmail();
        if (recipient == null || recipient.isBlank() || !recipient.contains("@")) {
            log.warn("Digest skipped for a user with an invalid stored email address");
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
                    .body(Map.of(
                            "from", from,
                            "to", List.of(recipient),
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