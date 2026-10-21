package com.pgds.assistant;

import com.pgds.common.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class RequestLimiterTest {

    static class MovableClock extends Clock {
        long ms = 1_000_000;
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId z) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(ms); }
        @Override public long millis() { return ms; }
    }

    @Test
    void blocksAfterLimitAndRecoversAfterAMinute() {
        MovableClock clock = new MovableClock();
        RequestLimiter l = new RequestLimiter(3, clock);
        l.check("a"); l.check("a"); l.check("a");
        assertThrows(ApiException.class, () -> l.check("a"));
        l.check("b");                                   // other users are unaffected
        clock.ms += 61_000;
        l.check("a");
    }
}
