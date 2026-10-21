package com.pgds.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {

    /** Clock the test can move forward. */
    static class MovableClock extends Clock {
        Instant now = Instant.parse("2026-10-09T10:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void locksAfterMaxFailuresAndUnlocksAfterDuration() {
        MovableClock clock = new MovableClock();
        LoginAttemptService svc = new LoginAttemptService(3, 15, clock);
        svc.failed("Alice"); svc.failed("alice");
        assertFalse(svc.isLocked("alice"));
        svc.failed("ALICE");
        assertTrue(svc.isLocked("alice"), "username match is case-insensitive");
        clock.advance(Duration.ofMinutes(16));
        assertFalse(svc.isLocked("alice"));
    }

    @Test
    void successResetsTheCounter() {
        LoginAttemptService svc = new LoginAttemptService(3, 15, new MovableClock());
        svc.failed("bob"); svc.failed("bob");
        svc.succeeded("bob");
        svc.failed("bob");
        assertFalse(svc.isLocked("bob"));
    }

    @Test
    void usersAreIndependent() {
        LoginAttemptService svc = new LoginAttemptService(2, 15, new MovableClock());
        svc.failed("carol"); svc.failed("carol");
        assertTrue(svc.isLocked("carol"));
        assertFalse(svc.isLocked("dave"));
    }
}
