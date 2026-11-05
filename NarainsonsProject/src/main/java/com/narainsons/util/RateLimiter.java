package com.narainsons.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fixed-window limiter. Counts reset on restart and are per server, which is enough
 * for a single Tomcat. Behind a reverse proxy every client shares one IP, so raise the IP limits
 * or read the client IP from the proxy header before relying on them.
 */
public final class RateLimiter {

    private static final int MAX_TRACKED_KEYS = 50_000;

    private record Window(long startMillis, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /** @return true if this call is within the limit */
    public boolean tryAcquire(String key, int maxCalls, long windowMillis, long nowMillis) {
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.entrySet().removeIf(e -> nowMillis - e.getValue().startMillis() > 24 * 60 * 60 * 1000L);
        }
        Window updated = windows.compute(key, (k, w) -> (w == null || nowMillis - w.startMillis() >= windowMillis)
                ? new Window(nowMillis, 1)
                : new Window(w.startMillis(), w.count() + 1));
        return updated.count() <= maxCalls;
    }

    private static final RateLimiter SHARED = new RateLimiter();

    /** Throws 429 when the key is over its limit. */
    public static void check(String key, int maxCalls, int windowMinutes) {
        if (!SHARED.tryAcquire(key, maxCalls, windowMinutes * 60_000L, System.currentTimeMillis())) {
            throw ApiException.tooManyRequests("Too many attempts. Please try again after some time.");
        }
    }
}
