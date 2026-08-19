package com.pantrytracker.email;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.pantrytracker.notification.ExpiryDigestTemplate;
import com.pantrytracker.notification.NotificationType;
import com.pantrytracker.user.User;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResendClientTest {

    private final ResendClient client = new ResendClient("", "Pantry Tracker <onboarding@resend.dev>");

    @Test
    void invalidRecipientIsSkippedWithoutSending() {
        User user = new User("not-an-email", "hash", "Test");

        assertThatCode(() -> client.sendDigest(user,
                List.of(new ExpiryDigestTemplate.DigestLine("Milk", LocalDate.now(), NotificationType.EXPIRING_SOON, 2)),
                List.of())).doesNotThrowAnyException();
    }

    @Test
    void blankRecipientIsSkippedWithoutSending() {
        User user = new User("", "hash", "Test");

        assertThatCode(() -> client.sendDigest(user,
                List.of(new ExpiryDigestTemplate.DigestLine("Milk", LocalDate.now(), NotificationType.EXPIRING_SOON, 2)),
                List.of())).doesNotThrowAnyException();
    }

    @Test
    void nullUserIsSkippedWithoutSending() {
        assertThatCode(() -> client.sendDigest(null,
                List.of(new ExpiryDigestTemplate.DigestLine("Milk", LocalDate.now(), NotificationType.EXPIRING_SOON, 2)),
                List.of())).doesNotThrowAnyException();
    }

    @Test
    void emptyDigestIsSkipped() {
        User user = new User("a@example.com", "hash", "Test");

        assertThatCode(() -> client.sendDigest(user, List.of(), List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void dryRunWithValidRecipientDoesNotThrow() {
        User user = new User("a@example.com", "hash", "Test");

        assertThatCode(() -> client.sendDigest(user,
                List.of(new ExpiryDigestTemplate.DigestLine("Milk", LocalDate.now(), NotificationType.EXPIRING_SOON, 2)),
                List.of(new ExpiryDigestTemplate.DigestLine("Bread", LocalDate.now().minusDays(1), NotificationType.EXPIRED, -1))))
                .doesNotThrowAnyException();
    }
}