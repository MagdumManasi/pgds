package com.pgds.assistant;

import com.pgds.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** Sliding one-minute window per user, so one account cannot run up model costs. */
@Component
public class RequestLimiter {

    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final int perMinute;
    private final Clock clock;

    public RequestLimiter(@Value("${pgds.assistant.requests-per-minute:20}") int perMinute, Clock clock) {
        this.perMinute = perMinute;
        this.clock = clock;
    }

    public void check(String user) {
        long now = clock.millis();
        Deque<Long> q = hits.computeIfAbsent(user, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > 60_000) q.pollFirst();
            if (q.size() >= perMinute)
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many questions. Please wait a minute and try again.");
            q.addLast(now);
        }
    }
}
