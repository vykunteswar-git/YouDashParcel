package com.youdash.service;

/**
 * Per-rider rate limiter for location updates.
 * Swap implementation for Redis-backed version when scaling horizontally.
 */
public interface LocationUpdateRateLimiter {

    /**
     * Returns true if this rider is allowed to update now; false if rate-limited.
     */
    boolean tryAcquire(Long riderId);
}
