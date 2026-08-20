package com.pantrytracker.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * In-memory, per-IP rate limiter for the public authentication endpoints
 * (login, register, refresh). Blocks brute-force and credential-stuffing
 * attempts with HTTP 429 after a conservative number of attempts per minute.
 *
 * Key = endpoint + client IP, value = request count + window start time.
 * The map is a ConcurrentHashMap and every check is a single atomic
 * compute(...) operation, so concurrent requests cannot bypass the limit by
 * racing the counter.
 *
 * Expired windows are swept away periodically (at most once per window) so
 * memory does not grow unboundedly under a flood of distinct IPs.
 *
 * Client IP resolution: the first non-blank value of X-Forwarded-For is used
 * when present (standard behavior behind reverse proxies), otherwise the
 * servlet remote address. Note: when NOT behind a trusted proxy, a client can
 * spoof X-Forwarded-For — per-IP limits are only fully meaningful behind one.
 *
 * This is a single-instance limiter: it is not shared across multiple app
 * instances. See the audit notes on the remaining limitations.
 */
@Component
public class AuthRateLimiter {

    static final int LOGIN_LIMIT = 5;
    static final int REGISTER_LIMIT = 3;
    static final int REFRESH_LIMIT = 10;
    static final long WINDOW_MILLIS = 60_000L;

    private final Clock clock;
    private final Map<String, Entry> counters = new ConcurrentHashMap<>();
    private final AtomicLong lastSweepMillis = new AtomicLong(0L);

    public AuthRateLimiter() {
        this(Clock.systemUTC());
    }

    /** Package-private so tests can inject a controllable clock. */
    AuthRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean allowLogin(String ip) {
        return allow("login", ip, LOGIN_LIMIT);
    }

    public boolean allowRegister(String ip) {
        return allow("register", ip, REGISTER_LIMIT);
    }

    public boolean allowRefresh(String ip) {
        return allow("refresh", ip, REFRESH_LIMIT);
    }

    /**
     * Resolves the client IP for rate limiting. Uses the first non-blank
     * value of X-Forwarded-For (supporting standard reverse proxies), falling
     * back to the servlet remote address.
     */
    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null) {
            for (String candidate : forwarded.split(",")) {
                String ip = candidate.trim();
                if (!ip.isBlank()) {
                    return ip;
                }
            }
        }
        return request.getRemoteAddr();
    }

    private boolean allow(String endpoint, String ip, int limit) {
        long now = clock.millis();
        sweepIfNeeded(now);
        String key = endpoint + ":" + ip;
        long[] count = new long[1];
        counters.compute(key, (k, entry) -> {
            if (entry == null || now - entry.windowStartMillis >= WINDOW_MILLIS) {
                count[0] = 1;
                return new Entry(now, 1);
            }
            count[0] = entry.count + 1;
            return new Entry(entry.windowStartMillis, entry.count + 1);
        });
        return count[0] <= limit;
    }

    private void sweepIfNeeded(long now) {
        long last = lastSweepMillis.get();
        if (now - last >= WINDOW_MILLIS && lastSweepMillis.compareAndSet(last, now)) {
            counters.entrySet().removeIf(e -> now - e.getValue().windowStartMillis >= WINDOW_MILLIS);
        }
    }

    private record Entry(long windowStartMillis, long count) {}
}