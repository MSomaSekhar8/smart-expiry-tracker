package com.pantrytracker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AuthRateLimiterTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private static final class TestClock extends Clock {
        private Instant instant;
        private final ZoneId zone = ZoneOffset.UTC;

        TestClock(Instant start) {
            this.instant = start;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private AuthRateLimiter limiter(TestClock clock) {
        return new AuthRateLimiter(clock);
    }

    @Test
    void loginAllowsFiveAttemptsPerMinuteThenBlocks() {
        TestClock clock = new TestClock(START);
        AuthRateLimiter limiter = limiter(clock);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.allowLogin("10.0.0.1")).isTrue();
        }
        assertThat(limiter.allowLogin("10.0.0.1")).isFalse();
    }

    @Test
    void registerAllowsThreeAttemptsPerMinuteThenBlocks() {
        TestClock clock = new TestClock(START);
        AuthRateLimiter limiter = limiter(clock);

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.allowRegister("10.0.0.1")).isTrue();
        }
        assertThat(limiter.allowRegister("10.0.0.1")).isFalse();
    }

    @Test
    void refreshAllowsTenAttemptsPerMinuteThenBlocks() {
        TestClock clock = new TestClock(START);
        AuthRateLimiter limiter = limiter(clock);

        for (int i = 0; i < 10; i++) {
            assertThat(limiter.allowRefresh("10.0.0.1")).isTrue();
        }
        assertThat(limiter.allowRefresh("10.0.0.1")).isFalse();
    }

    @Test
    void limitsAreIndependentPerIp() {
        TestClock clock = new TestClock(START);
        AuthRateLimiter limiter = limiter(clock);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.allowLogin("10.0.0.1")).isTrue();
        }
        assertThat(limiter.allowLogin("10.0.0.1")).isFalse();

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.allowLogin("10.0.0.2")).isTrue();
        }
        assertThat(limiter.allowLogin("10.0.0.2")).isFalse();
    }

    @Test
    void endpointsAreCountedSeparatelyForTheSameIp() {
        TestClock clock = new TestClock(START);
        AuthRateLimiter limiter = limiter(clock);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.allowLogin("10.0.0.1")).isTrue();
        }
        assertThat(limiter.allowLogin("10.0.0.1")).isFalse();

        assertThat(limiter.allowRegister("10.0.0.1")).isTrue();
        assertThat(limiter.allowRefresh("10.0.0.1")).isTrue();
    }

    @Test
    void windowExpiryResetsTheCounter() {
        TestClock clock = new TestClock(START);
        AuthRateLimiter limiter = limiter(clock);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.allowLogin("10.0.0.1")).isTrue();
        }
        assertThat(limiter.allowLogin("10.0.0.1")).isFalse();

        clock.advance(Duration.ofSeconds(61));

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.allowLogin("10.0.0.1")).isTrue();
        }
        assertThat(limiter.allowLogin("10.0.0.1")).isFalse();
    }

    @Test
    void expiredEntriesAreRemovedSoMemoryDoesNotGrowUnboundedly() throws Exception {
        TestClock clock = new TestClock(START);
        AuthRateLimiter limiter = limiter(clock);

        for (int i = 0; i < 6; i++) {
            assertThat(limiter.allowLogin("10.0.0." + (i + 1))).isTrue();
        }
        assertThat(trackedBuckets(limiter)).isEqualTo(6);

        clock.advance(Duration.ofMinutes(2));
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.allowLogin("10.0.1." + (i + 1))).isTrue();
        }

        assertThat(trackedBuckets(limiter)).isEqualTo(3);
    }

    @Test
    void concurrentRequestsCannotExceedTheConfiguredLimit() throws Exception {
        AuthRateLimiter limiter = limiter(new TestClock(START));
        int threadCount = 20;
        AtomicInteger allowed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < threadCount; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 10; i++) {
                        if (limiter.allowLogin("10.0.0.9")) {
                            allowed.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(allowed.get()).isEqualTo(5);
    }

    @SuppressWarnings("unchecked")
    private int trackedBuckets(AuthRateLimiter limiter) throws Exception {
        Field field = AuthRateLimiter.class.getDeclaredField("counters");
        field.setAccessible(true);
        return ((Map<String, ?>) field.get(limiter)).size();
    }
}