package com.pantrytracker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pantrytracker.category.Category;
import com.pantrytracker.item.Item;
import com.pantrytracker.user.User;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationRecorderTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationRecorder recorder;
    private User user;
    private Item item;

    @BeforeEach
    void setUp() {
        recorder = new NotificationRecorder(notificationRepository);
        user = new User("a@example.com", "hash", "Test");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        Category category = new Category("grocery", 30, 3);
        item = new Item(user, category, "Milk");
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
    }

    @Test
    void recordPersistsTheNotificationWithSaveAndFlush() {
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(recorder.record(user, item, NotificationType.EXPIRING_SOON)).isTrue();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getItem()).isSameAs(item);
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getType()).isEqualTo(NotificationType.EXPIRING_SOON);
    }

    @Test
    void recordReturnsFalseWhenTheUniqueConstraintIsViolated() {
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatCode(() -> recorder.record(user, item, NotificationType.EXPIRED))
                .doesNotThrowAnyException();
        assertThat(recorder.record(user, item, NotificationType.EXPIRED)).isFalse();
    }

    @Test
    void alreadyNotifiedTodayQueriesSinceTheStartOfTheUtcDay() {
        when(notificationRepository.existsForItemToday(
                eq(item.getId()), eq(NotificationType.EXPIRING_SOON), any(Instant.class)))
                .thenReturn(true);

        assertThat(recorder.alreadyNotifiedToday(item, NotificationType.EXPIRING_SOON)).isTrue();

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(notificationRepository).existsForItemToday(
                eq(item.getId()), eq(NotificationType.EXPIRING_SOON), since.capture());
        Instant utcDayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(since.getValue()).isBetween(utcDayStart.minusSeconds(1), utcDayStart.plusSeconds(1));
    }
}