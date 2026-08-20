package com.pantrytracker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pantrytracker.category.Category;
import com.pantrytracker.email.ResendClient;
import com.pantrytracker.item.Item;
import com.pantrytracker.item.ItemRepository;
import com.pantrytracker.user.User;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExpiryDigestServiceTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private NotificationRecorder notificationRecorder;
    @Mock
    private ResendClient resendClient;

    private ExpiryDigestService digestService;
    private final List<DigestCall> calls = new ArrayList<>();
    private Function<User, Boolean> sendResult = user -> true;

    private record DigestCall(User user,
                              List<ExpiryDigestTemplate.DigestLine> expiringSoon,
                              List<ExpiryDigestTemplate.DigestLine> expired) {}

    @BeforeEach
    void setUp() {
        digestService = new ExpiryDigestService(itemRepository, notificationRecorder, resendClient);
        lenient().doAnswer(invocation -> {
            calls.add(new DigestCall(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2)));
            return sendResult.apply(invocation.getArgument(0));
        }).when(resendClient).sendDigest(any(), anyList(), anyList());
    }

    private User user(String email) {
        User user = new User(email, "hash", "Test");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private Item itemFor(User owner, String name, LocalDate expiry) {
        Category category = new Category("grocery", 30, 3);
        Item item = new Item(owner, category, name);
        item.setExpiryDate(expiry);
        return item;
    }

    @Test
    void sendsOneEmailPerUserContainingOnlyThatUsersItems() {
        User userA = user("a@example.com");
        User userB = user("b@example.com");
        Item aExpiring = itemFor(userA, "A-Milk", LocalDate.now().plusDays(2));
        Item aSafe = itemFor(userA, "A-Safe", LocalDate.now().plusDays(30));
        Item bExpired = itemFor(userB, "B-Pills", LocalDate.now().minusDays(1));
        when(itemRepository.findAllWithOwnerAndCategory()).thenReturn(List.of(aExpiring, aSafe, bExpired));
        when(notificationRecorder.alreadyNotifiedToday(any(), any())).thenReturn(false);
        when(notificationRecorder.record(any(), any(), any())).thenReturn(true);

        ExpiryDigestService.DigestReport report = digestService.run();

        assertThat(report.expiringSoonCount()).isEqualTo(1);
        assertThat(report.expiredCount()).isEqualTo(1);
        assertThat(calls).hasSize(2);

        DigestCall callForA = calls.stream()
                .filter(c -> c.user().getEmail().equals("a@example.com")).findFirst().orElseThrow();
        assertThat(callForA.user().getEmail()).isEqualTo("a@example.com");
        assertThat(callForA.expiringSoon()).extracting(ExpiryDigestTemplate.DigestLine::name)
                .containsExactly("A-Milk");
        assertThat(callForA.expired()).isEmpty();

        DigestCall callForB = calls.stream()
                .filter(c -> c.user().getEmail().equals("b@example.com")).findFirst().orElseThrow();
        assertThat(callForB.user().getEmail()).isEqualTo("b@example.com");
        assertThat(callForB.expired()).extracting(ExpiryDigestTemplate.DigestLine::name)
                .containsExactly("B-Pills");
        assertThat(callForB.expiringSoon()).isEmpty();
    }

    @Test
    void emailsNeverMixItemsFromDifferentUsers() {
        User userA = user("a@example.com");
        User userB = user("b@example.com");
        Item aItem = itemFor(userA, "A-Milk", LocalDate.now().plusDays(2));
        Item bItem = itemFor(userB, "B-Pills", LocalDate.now().minusDays(1));
        when(itemRepository.findAllWithOwnerAndCategory()).thenReturn(List.of(aItem, bItem));
        when(notificationRecorder.alreadyNotifiedToday(any(), any())).thenReturn(false);
        when(notificationRecorder.record(any(), any(), any())).thenReturn(true);

        digestService.run();

        for (DigestCall call : calls) {
            List<String> names = new ArrayList<>();
            call.expiringSoon().forEach(l -> names.add(l.name()));
            call.expired().forEach(l -> names.add(l.name()));
            if (call.user().getEmail().equals("a@example.com")) {
                assertThat(names).containsOnly("A-Milk");
            } else {
                assertThat(names).containsOnly("B-Pills");
            }
        }
        assertThat(calls).hasSize(2);
    }

    @Test
    void recipientIsAlwaysTheUsersOwnEmail() {
        User userA = user("a@example.com");
        User userB = user("b@example.com");
        when(itemRepository.findAllWithOwnerAndCategory()).thenReturn(List.of(
                itemFor(userA, "A-Milk", LocalDate.now().plusDays(2)),
                itemFor(userB, "B-Pills", LocalDate.now().minusDays(1))));
        when(notificationRecorder.alreadyNotifiedToday(any(), any())).thenReturn(false);
        when(notificationRecorder.record(any(), any(), any())).thenReturn(true);

        digestService.run();

        for (DigestCall call : calls) {
            assertThat(call.user().getEmail()).isNotBlank();
            assertThat(call.user().getEmail()).isNotEqualTo("user@pantrytracker.app");
        }
    }

    @Test
    void alreadyNotifiedItemsAreNotEmailedAgain() {
        User userA = user("a@example.com");
        Item notified = itemFor(userA, "A-Milk", LocalDate.now().plusDays(2));
        when(itemRepository.findAllWithOwnerAndCategory()).thenReturn(List.of(notified));
        when(notificationRecorder.alreadyNotifiedToday(any(), any())).thenReturn(true);

        ExpiryDigestService.DigestReport report = digestService.run();

        assertThat(report.expiringSoonCount()).isZero();
        assertThat(report.expiredCount()).isZero();
        assertThat(calls).isEmpty();
        verify(resendClient, never()).sendDigest(any(), anyList(), anyList());
        verify(notificationRecorder, never()).record(any(), any(), any());
    }

    @Test
    void safeItemsAreNotEmailed() {
        User userA = user("a@example.com");
        Item safe = itemFor(userA, "A-Safe", LocalDate.now().plusDays(30));
        when(itemRepository.findAllWithOwnerAndCategory()).thenReturn(List.of(safe));

        ExpiryDigestService.DigestReport report = digestService.run();

        assertThat(report.expiringSoonCount()).isZero();
        assertThat(report.expiredCount()).isZero();
        assertThat(calls).isEmpty();
    }

    @Test
    void successfulSendRecordsNotificationForEverySentItem() {
        User userA = user("a@example.com");
        Item aExpiring = itemFor(userA, "A-Milk", LocalDate.now().plusDays(2));
        Item aExpired = itemFor(userA, "A-Bread", LocalDate.now().minusDays(1));
        when(itemRepository.findAllWithOwnerAndCategory()).thenReturn(List.of(aExpiring, aExpired));
        when(notificationRecorder.alreadyNotifiedToday(any(), any())).thenReturn(false);
        when(notificationRecorder.record(any(), any(), any())).thenReturn(true);

        ExpiryDigestService.DigestReport report = digestService.run();

        assertThat(report.expiringSoonCount()).isEqualTo(1);
        assertThat(report.expiredCount()).isEqualTo(1);
        assertThat(calls).hasSize(1);
        verify(notificationRecorder).record(eq(userA), eq(aExpiring), eq(NotificationType.EXPIRING_SOON));
        verify(notificationRecorder).record(eq(userA), eq(aExpired), eq(NotificationType.EXPIRED));
    }

    @Test
    void failedSendRecordsNothingButOtherUsersStillGetEmails() {
        User userA = user("a@example.com");
        User userB = user("b@example.com");
        Item aExpiring = itemFor(userA, "A-Milk", LocalDate.now().plusDays(2));
        Item bExpired = itemFor(userB, "B-Pills", LocalDate.now().minusDays(1));
        when(itemRepository.findAllWithOwnerAndCategory()).thenReturn(List.of(aExpiring, bExpired));
        when(notificationRecorder.alreadyNotifiedToday(any(), any())).thenReturn(false);
        when(notificationRecorder.record(any(), any(), any())).thenReturn(true);
        sendResult = user -> !"b@example.com".equals(user.getEmail());

        ExpiryDigestService.DigestReport report = digestService.run();

        assertThat(report.expiringSoonCount()).isEqualTo(1);
        assertThat(report.expiredCount()).isZero();
        assertThat(calls).hasSize(2);
        verify(notificationRecorder).record(eq(userA), eq(aExpiring), eq(NotificationType.EXPIRING_SOON));
        verify(notificationRecorder, never()).record(eq(userB), eq(bExpired), eq(NotificationType.EXPIRED));
    }

    @Test
    void duplicateRecordDoesNotAbortTheDigestBatch() {
        User userA = user("a@example.com");
        User userB = user("b@example.com");
        Item dupExpiring = itemFor(userA, "A-Milk", LocalDate.now().plusDays(2));
        Item aExpired = itemFor(userA, "A-Bread", LocalDate.now().minusDays(1));
        Item bExpiring = itemFor(userB, "B-Cheese", LocalDate.now().plusDays(3));
        when(itemRepository.findAllWithOwnerAndCategory()).thenReturn(List.of(dupExpiring, aExpired, bExpiring));
        when(notificationRecorder.alreadyNotifiedToday(any(), any())).thenReturn(false);
        when(notificationRecorder.record(eq(userA), eq(dupExpiring), eq(NotificationType.EXPIRING_SOON)))
                .thenReturn(false); // a racing run recorded it first
        when(notificationRecorder.record(eq(userA), eq(aExpired), eq(NotificationType.EXPIRED))).thenReturn(true);
        when(notificationRecorder.record(eq(userB), eq(bExpiring), eq(NotificationType.EXPIRING_SOON))).thenReturn(true);

        ExpiryDigestService.DigestReport report = digestService.run();

        assertThat(report.expiringSoonCount()).isEqualTo(1);
        assertThat(report.expiredCount()).isEqualTo(1);
        assertThat(calls).hasSize(2);
        verify(notificationRecorder).record(eq(userA), eq(aExpired), eq(NotificationType.EXPIRED));
        verify(notificationRecorder).record(eq(userB), eq(bExpiring), eq(NotificationType.EXPIRING_SOON));
    }
}