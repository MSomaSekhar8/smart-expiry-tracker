package com.pantrytracker.email;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.pantrytracker.notification.ExpiryDigestTemplate;
import com.pantrytracker.user.User;

/**
 * Sends email through the Resend HTTP API.
 * When no API key is configured (local dev), the digest is only logged
 * as counts — no item names, no email addresses, no tokens.
 */
@Component
public class ResendClient {

    private static final Logger log =
            LoggerFactory.getLogger(ResendClient.class);

    private final RestClient restClient;
    private final String from;
    private final boolean enabled;

    final int connectTimeoutMillis;
    final int readTimeoutMillis;

    @Autowired
    public ResendClient(
            @Value("${app.resend.api-key:}") String apiKey,
            @Value("${app.resend.from:}") String from) {

        this(
                apiKey,
                from,
                "https://api.resend.com",
                5_000,
                10_000
        );
    }

    /**
     * Package-private so tests can point at a local HTTP server
     * and shrink the timeouts.
     */
    ResendClient(
            String apiKey,
            String from,
            String baseUrl,
            int connectTimeoutMillis,
            int readTimeoutMillis) {

        this.enabled = apiKey != null && !apiKey.isBlank();
        this.from = from;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;

        this.restClient = enabled
                ? RestClient.builder()
                        .baseUrl(baseUrl)
                        .requestFactory(
                                requestFactory(
                                        connectTimeoutMillis,
                                        readTimeoutMillis
                                )
                        )
                        .defaultHeader(
                                "Authorization",
                                "Bearer " + apiKey
                        )
                        .build()
                : null;
    }

    private static ClientHttpRequestFactory requestFactory(
            int connectTimeoutMillis,
            int readTimeoutMillis) {

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(connectTimeoutMillis);
        factory.setReadTimeout(readTimeoutMillis);

        return factory;
    }

    /**
     * Sends ONE digest to the given user containing ONLY that user's items.
     *
     * @return true when the email was accepted for delivery
     *         (or the dry-run was logged), false otherwise.
     */
    public boolean sendDigest(
            User user,
            List<ExpiryDigestTemplate.DigestLine> expiringSoon,
            List<ExpiryDigestTemplate.DigestLine> expired) {

        if (expiringSoon.isEmpty() && expired.isEmpty()) {
            return false;
        }

        String recipient = user == null ? null : user.getEmail();

        if (recipient == null
                || recipient.isBlank()
                || !recipient.contains("@")) {

            log.warn(
                    "Digest skipped for a user with an invalid stored email address"
            );

            return false;
        }

        String html =
                ExpiryDigestTemplate.render(expiringSoon, expired);

        if (!enabled) {
            log.info(
                    "[digest dry-run] {} expiring soon, {} expired — RESEND_API_KEY not set",
                    expiringSoon.size(),
                    expired.size()
            );

            return true;
        }

        try {
            restClient.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", from,
                            "to", List.of(recipient),
                            "subject",
                                    "Pantry digest: "
                                    + expiringSoon.size()
                                    + " expiring, "
                                    + expired.size()
                                    + " expired",
                            "html", html
                    ))
                    .retrieve()
                    .toBodilessEntity();

            return true;

        } catch (Exception ex) {

            log.warn(
                    "Digest email send failed: {}",
                    ex.getClass().getSimpleName()
            );

            return false;
        }
    }
}