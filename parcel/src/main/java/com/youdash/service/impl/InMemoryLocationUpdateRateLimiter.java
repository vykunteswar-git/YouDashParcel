package com.youdash.service.impl;

import com.youdash.service.LocationUpdateRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryLocationUpdateRateLimiter implements LocationUpdateRateLimiter {

    @Value("${youdash.tracking.min-update-interval-ms:3000}")
    private long minIntervalMs;

    private final ConcurrentHashMap<Long, Long> lastUpdateMs = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(Long riderId) {
        long now = System.currentTimeMillis();
        Long last = lastUpdateMs.get(riderId);
        if (last != null && (now - last) < minIntervalMs) {
            return false;
        }
        lastUpdateMs.put(riderId, now);
        return true;
    }
}
