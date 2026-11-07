package edu.tku.registration.support;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Mutable fixed Clock (zone Asia/Taipei) plus the named instants of spec FR4-FR5-FR7. Not a test class. */
public class MutableTestClock extends Clock {

    public static final String T_OPEN = "2026-09-01T09:00:00+08:00";
    public static final String T_START = "2026-08-01T00:00:00+08:00";
    public static final String T_BEFORE_START = "2026-07-31T23:59:59+08:00";
    public static final String T_END = "2026-12-31T23:59:59+08:00";
    public static final String T_END_MS = "2026-12-31T23:59:59.999+08:00";
    public static final String T_AFTER_END = "2027-01-01T00:00:00+08:00";
    public static final String T_SPRING = "2026-02-01T09:00:00+08:00";
    public static final String T_LATE = "2027-06-01T09:00:00+08:00";

    private final ZoneId zone = ZoneId.of("Asia/Taipei");
    private volatile Instant instant = OffsetDateTime.parse(T_OPEN).toInstant();

    public void set(String offsetDateTime) {
        this.instant = OffsetDateTime.parse(offsetDateTime).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId z) {
        return this;
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
