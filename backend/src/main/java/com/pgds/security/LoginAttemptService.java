package com.pgds.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Locks a username for a while after repeated failed sign-ins (brute-force protection).
 * In-memory: state resets on restart and is per instance. Use a shared store (Redis) if you run several backend instances.
 */
@Component
public class LoginAttemptService {

    private record State(int failures, Instant lockedUntil) {}

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration lockDuration;
    private final Clock clock;

    public LoginAttemptService(@Value("${pgds.security.max-login-failures:5}") int maxFailures,
                               @Value("${pgds.security.lock-minutes:15}") long lockMinutes, Clock clock) {
        this.maxFailures = maxFailures;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
        this.clock = clock;
    }

    public boolean isLocked(String username) {
        State s = states.get(key(username));
        return s != null && s.lockedUntil() != null && Instant.now(clock).isBefore(s.lockedUntil());
    }

    public void failed(String username) {
        Instant now = Instant.now(clock);
        states.compute(key(username), (k, old) -> {
            int failures = (old == null || (old.lockedUntil() != null && !now.isBefore(old.lockedUntil()))) ? 1 : old.failures() + 1;
            return new State(failures, failures >= maxFailures ? now.plus(lockDuration) : null);
        });
    }

    public void succeeded(String username) { states.remove(key(username)); }

    private static String key(String username) { return username == null ? "" : username.toLowerCase(Locale.ROOT); }
}
