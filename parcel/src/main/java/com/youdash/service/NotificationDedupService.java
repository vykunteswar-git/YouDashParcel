package com.youdash.service;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Best-effort in-process deduplication (e.g. verify + webhook both marking PAID).
 * For multi-instance production, replace with Redis SETNX or a notification_outbox table.
 */
@Service
public class NotificationDedupService {

    private final ConcurrentHashMap<String, Long> lastSentMs = new ConcurrentHashMap<>();

    @Value("${notification.dedup.ttl-ms:300000}")
    private long ttlMs;

    /**
     * @return true if this key was not sent recently (caller should send).
     */
    public boolean tryAcquire(String dedupKey) {
        if (dedupKey == null || dedupKey.isBlank()) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long prev = lastSentMs.putIfAbsent(dedupKey, now);
        if (prev == null) {
            prune(now);
            return true;
        }
        if (now - prev < ttlMs) {
            return false;
        }
        lastSentMs.put(dedupKey, now);
        prune(now);
        return true;
    }

    private void prune(long now) {
        if (lastSentMs.size() < 10_000) {
            return;
        }
        lastSentMs.entrySet().removeIf(e -> now - e.getValue() > ttlMs * 4L);
    }
}
